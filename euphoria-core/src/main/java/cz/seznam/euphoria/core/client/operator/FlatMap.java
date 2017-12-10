/**
 * Copyright 2016-2017 Seznam.cz, a.s.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.seznam.euphoria.core.client.operator;

import cz.seznam.euphoria.core.annotation.audience.Audience;
import cz.seznam.euphoria.core.client.functional.ExtractEventTime;
import cz.seznam.euphoria.core.annotation.operator.Basic;
import cz.seznam.euphoria.core.annotation.operator.StateComplexity;
import cz.seznam.euphoria.core.client.dataset.Dataset;
import cz.seznam.euphoria.core.client.flow.Flow;
import cz.seznam.euphoria.core.client.functional.UnaryFunctor;
import cz.seznam.euphoria.core.client.io.Collector;
import cz.seznam.euphoria.shadow.com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A transformation of a dataset from one type into another allowing user code
 * to generate zero, one, or many output elements for a given input element.<p>
 *
 * The user supplied map function is supposed to be stateless. It is fed items
 * from the input in no specified order and the results of the map function are
 * "flattened" to the output (equally in no specified order.)<p>
 *
 * Example:
 *
 * <pre>{@code
 *  Dataset<String> strings = ...;
 *  Dataset<Integer> ints =
 *         FlatMap.named("TO-INT")
 *            .of(strings)
 *            .using((String s, Context<String> c) -> {
 *              try {
 *                int i = Integer.parseInt(s);
 *                c.collect(i);
 *              } catch (NumberFormatException e) {
 *                // ~ ignore the input if we failed to parse it
 *              }
 *            })
 *            .output();
 * }</pre>
 *
 * The above example tries to parse incoming strings as integers, silently
 * skipping those which cannot be successfully converted. While
 * {@link Collector#collect(Object)} has
 * been used only once here, a {@link FlatMap} operator is free
 * to invoke it multiple times or not at all to generate that many elements
 * to the output dataset.
 */
@Audience(Audience.Type.CLIENT)
@Basic(
    state = StateComplexity.ZERO,
    repartitions = 0
)
public class FlatMap<IN, OUT> extends ElementWiseOperator<IN, OUT> implements SideOutputAware {

  public static class OfBuilder implements Builders.Of {
    private final String name;

    OfBuilder(String name) {
      this.name = name;
    }

    @Override
    public <IN> UsingBuilder<IN> of(Dataset<IN> input) {
      return new UsingBuilder<>(name, input);
    }
  }

  public static class UsingBuilder<IN> {
    private final String name;
    private final Dataset<IN> input;

    UsingBuilder(String name, Dataset<IN> input) {
      this.name = Objects.requireNonNull(name);
      this.input = Objects.requireNonNull(input);
    }

    /**
     * Specifies the user defined map function by which to transform
     * the final operator's input dataset.
     *
     * @param <OUT> the type of elements the user defined map function
     *            will produce to the output dataset
     *
     * @param functor the user defined map function
     *
     * @return the next builder to complete the setup
     *          of the {@link FlatMap} operator
     */
    public <OUT> EventTimeBuilder<IN, OUT> using(UnaryFunctor<IN, OUT> functor) {
      return new EventTimeBuilder<>(name, input, functor);
    }
  }

  public static class EventTimeBuilder<IN, OUT> implements Builders.SideOutput<OUT> {

    private final String name;
    private final Dataset<IN> input;
    private final UnaryFunctor<IN, OUT> functor;

    EventTimeBuilder(
        String name,
        Dataset<IN> input,
        UnaryFunctor<IN, OUT> functor) {
      this.name = name;
      this.input = input;
      this.functor = Objects.requireNonNull(functor);
    }

    /**
     * Specifies a function to derive the input elements' event time. Processing
     * of the input stream continues then to proceed with this event time.
     *
     * @param eventTimeFn the event time extraction function
     *
     * @return the next builder to complete the setup
     *          of the {@link FlatMap} operator
     */
    public SideOutputBuilder<IN, OUT> eventTimeBy(ExtractEventTime<IN> eventTimeFn) {
      return new SideOutputBuilder<>(name, input, functor, eventTimeFn);
    }

    @Override
    public OutputBuilder<IN, OUT> withSideOutputs(SideOutput<?>... sideOutputs) {
      return eventTimeBy(null).withSideOutputs(sideOutputs);
    }

    @Override
    public Dataset<OUT> output() {
      return eventTimeBy(null).withSideOutputs().output();
    }
  }

  public static class SideOutputBuilder<IN, OUT> implements Builders.SideOutput<OUT> {

    private final String name;
    private final Dataset<IN> input;
    private final UnaryFunctor<IN, OUT> functor;
    @Nullable
    private final ExtractEventTime<IN> evtTimeFn;

    SideOutputBuilder(
        String name,
        Dataset<IN> input,
        UnaryFunctor<IN, OUT> functor,
        @Nullable ExtractEventTime<IN> evtTimeFn) {
      this.name = name;
      this.input = input;
      this.functor = functor;
      this.evtTimeFn = evtTimeFn;
    }

    @Override
    public OutputBuilder<IN, OUT> withSideOutputs(SideOutput<?>... sideOutputs) {
      return new OutputBuilder<>(name, input, functor, evtTimeFn, Sets.newHashSet(sideOutputs));
    }

    @Override
    public Dataset<OUT> output() {
      return withSideOutputs().output();
    }
  }

  public static class OutputBuilder<IN, OUT> implements Builders.Output<OUT> {

    private final String name;
    private final Dataset<IN> input;
    private final UnaryFunctor<IN, OUT> functor;
    @Nullable
    private final ExtractEventTime<IN> evtTimeFn;
    private final Set<SideOutput<?>> sideOutputs;

    OutputBuilder(
        String name,
        Dataset<IN> input,
        UnaryFunctor<IN, OUT> functor,
        @Nullable ExtractEventTime<IN> evtTimeFn,
        Set<SideOutput<?>> sideOutputs) {
      this.name = Objects.requireNonNull(name);
      this.input = Objects.requireNonNull(input);
      this.functor = Objects.requireNonNull(functor);
      this.evtTimeFn = evtTimeFn;
      this.sideOutputs = Objects.requireNonNull(sideOutputs);
    }

    @Override
    public Dataset<OUT> output() {
      final Flow flow = input.getFlow();
      final FlatMap<IN, OUT> map = new FlatMap<>(
          name, flow, input, functor, evtTimeFn, sideOutputs);
      flow.add(map);
      return map.output();
    }

  }

  /**
   * Starts building a nameless {@link FlatMap} operator to transform the given
   * input dataset.
   *
   * @param <IN> the type of elements of the input dataset
   *
   * @param input the input data set to be transformed
   *
   * @return a builder to complete the setup of the new {@link FlatMap} operator
   *
   * @see #named(String)
   * @see OfBuilder#of(Dataset)
   */
  public static <IN> UsingBuilder<IN> of(Dataset<IN> input) {
    return new UsingBuilder<>("FlatMap", input);
  }

  /**
   * Starts building a named {@link FlatMap} operator.
   *
   * @param name a user provided name of the new operator to build
   *
   * @return a builder to complete the setup of the new {@link FlatMap} operator
   */
  public static OfBuilder named(String name) {
    return new OfBuilder(name);
  }

  private final UnaryFunctor<IN, OUT> functor;
  private final ExtractEventTime<IN> eventTimeFn;
  private final Set<SideOutput<?>> sideOutputs;

  FlatMap(
      String name,
      Flow flow,
      Dataset<IN> input,
      UnaryFunctor<IN, OUT> functor,
      @Nullable ExtractEventTime<IN> evtTimeFn,
      Set<SideOutput<?>> sideOutputs) {
    super(name, flow, input);
    this.functor = Objects.requireNonNull(functor);
    this.eventTimeFn = evtTimeFn;
    this.sideOutputs = Objects.requireNonNull(sideOutputs);
  }

  /**
   * Retrieves the user defined map function to be applied to this operator's
   * input elements.
   *
   * @return the user defined map function; never {@code null}
   */
  public UnaryFunctor<IN, OUT> getFunctor() {
    return functor;
  }

  /**
   * Retrieves the optional user defined event time assigner.
   *
   * @return the user defined event time assigner or
   *          {@code null} if none is specified
   */
  @Nullable
  public ExtractEventTime<IN> getEventTimeExtractor() {
    return eventTimeFn;
  }

  @Override
  public Set<SideOutput<?>> getSideOutputs() {
    return sideOutputs;
  }
}