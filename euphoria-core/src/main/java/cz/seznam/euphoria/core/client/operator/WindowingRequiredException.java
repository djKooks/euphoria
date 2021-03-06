/*
 * Copyright 2016-2018 Seznam.cz, a.s.
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

/**
 * Thrown by executors at flow submission time when an invalid flow set up is detected,
 * requiring the user to explicitly provide a windowing strategy to a certain operator.
 */
@Audience(Audience.Type.EXECUTOR)
public class WindowingRequiredException extends IllegalStateException {
  public WindowingRequiredException(String message) {
    super(message);
  }
}
