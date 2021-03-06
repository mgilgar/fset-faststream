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

package scheduler.fsb

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.application.FsbService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object EvaluateFsbJob extends EvaluateFsbJob {
  val fsbService: FsbService = FsbService
}

trait EvaluateFsbJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val fsbService: FsbService

  val config: EvaluateFsbJobConfig.type = EvaluateFsbJobConfig

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    Logger.debug(s"EvaluateFsbJob starting")
    fsbService.nextFsbCandidateReadyForEvaluation.flatMap { appIdOpt =>
      appIdOpt.map { appId =>
        Logger.debug(s"EvaluateFsbJob found a candidate - now evaluating...")
        fsbService.evaluateFsbCandidate(appId)
      }.getOrElse {
        Logger.debug(s"EvaluateFsbJob no candidates found - going back to sleep...")
        Future.successful(())
      }
    }
  }
}

object EvaluateFsbJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.evaluate-fsb-job",
  name = "EvaluateFsbJob"
)
