/*
 * Copyright 2017 HM Revenue & Customs
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

package model

import model.persisted.CubiksTest
import model.persisted.TestResult
import org.joda.time.DateTime

object Phase1TestExamples {
  val testResult = TestResult("Ready", "norm", Some(12.5), None, None, None)

  def createTestResult(tScore: Double) = TestResult("Ready", "norm", Some(tScore), None, None, None)

  def firstTest(implicit now: DateTime) = CubiksTest(16196, usedForResults = true, 2, "cubiks", "token", "http://localhost", now, 3,
    testResult = Some(testResult))

  def secondTest(implicit now: DateTime) = firstTest.copy(scheduleId = 16194)

  def thirdTest(implicit now: DateTime) = firstTest.copy(scheduleId = 16196)
}
