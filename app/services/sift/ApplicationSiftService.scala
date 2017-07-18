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

package services.sift

import common.FutureEx
import model.SerialUpdateResult
import model.command.ApplicationForSift
import repositories.sift.{ ApplicationSiftMongoRepository, ApplicationSiftRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

object ApplicationSiftService extends ApplicationSiftService {
  val applicationSiftRepo: ApplicationSiftMongoRepository = repositories.applicationSiftRepository
}

trait ApplicationSiftService {
  def applicationSiftRepo: ApplicationSiftRepository

  def nextApplicationsReadyForSift(batchSize: Int): Future[Seq[ApplicationForSift]] = {
    applicationSiftRepo.nextApplicationsForSift(batchSize)
  }

  def progressApplicationToSift(applications: Seq[ApplicationForSift]): Future[SerialUpdateResult[ApplicationForSift]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application, applicationSiftRepo.progressApplicationToSift(application))
    }

    updates.map(SerialUpdateResult.fromEither)
  }

}
