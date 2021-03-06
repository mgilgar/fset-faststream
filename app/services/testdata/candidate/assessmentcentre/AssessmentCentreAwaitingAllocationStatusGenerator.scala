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

package services.testdata.candidate.assessmentcentre

import model.ApplicationStatus
import model.EvaluationResults.Green
import model.command.ApplicationForProgression
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.SchemeEvaluationResult
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import services.assessmentcentre.AssessmentCentreService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.sift.SiftEnteredStatusGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreAwaitingAllocationStatusGenerator extends AssessmentCentreAwaitingAllocationStatusGenerator {
  override val previousStatusGenerator = SiftEnteredStatusGenerator
  override val assessmentCentreService = AssessmentCentreService
}

trait AssessmentCentreAwaitingAllocationStatusGenerator extends ConstructiveGenerator {
  val assessmentCentreService: AssessmentCentreService

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      siftResults = candidateInPreviousStatus.schemePreferences.get.schemes.map(scheme => SchemeEvaluationResult(scheme, Green.toString))
      _ <- assessmentCentreService.progressApplicationsToAssessmentCentre(
        Seq(ApplicationForProgression(candidateInPreviousStatus.applicationId.get, ApplicationStatus.SIFT, siftResults
        )))
    } yield {
      candidateInPreviousStatus
    }
  }
}
