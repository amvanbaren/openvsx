/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter

import io.gatling.core.Predef._
import org.eclipse.openvsx.Scenarios._

class VSCodeAdapterGetWebResourceSimulation extends Simulation {
  setUp(getWebResourceScenario().inject(atOnceUsers(users))).protocols(httpProtocol)
}
