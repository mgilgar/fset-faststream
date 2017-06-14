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

package controllers.testdata

import play.api.mvc.Action
import scheduler.onlinetesting.{ EvaluatePhase3ResultJob, EvaluatePhase1ResultJob }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestJobsController extends TestJobsController

class TestJobsController extends BaseController {

  def evaluatePhase1OnlineTestsCandidate = Action.async { implicit request =>
    EvaluatePhase1ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 1 result job started")
    }
  }

  def evaluatePhase3VideoInterviewCandidate = Action.async { implicit request =>
    EvaluatePhase3ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 3 result job started")
    }
  }
}
