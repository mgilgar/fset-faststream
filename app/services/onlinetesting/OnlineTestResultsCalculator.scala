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

package services.onlinetesting

import model.EvaluationResults.{ Amber, Green, Red, Result }
import model.exchange.passmarksettings.PassMarkThreshold
import play.api.Logger

trait OnlineTestResultsCalculator {

  def evaluateTestResult(threshold: PassMarkThreshold)(tScore: Option[Double]): Result = {
    val failmark = threshold.failThreshold
    val passmark = threshold.passThreshold
    tScore match {
      case Some(score) if score >= passmark => Green
      case Some(score) if score < failmark => Red
      case Some(_) => Amber
      case _ => throw new IllegalArgumentException("Score not found")
    }
  }

  def combineTestResults(results: Result*) = {
    require(results.nonEmpty, "Test results not found")
    val result = results match {
      case _ if results.contains(Red) => Red
      case _ if results.contains(Amber) => Amber
      case _ if results.forall(_ == Green) => Green
    }
    Logger.debug(s"Combining results $results = $result")
    result
  }
}
