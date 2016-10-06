/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories.onlinetesting

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.OnlineTestApplication
import model.persisted.{ CubiksTest, Phase2TestProfile }
import model.ApplicationStatus
import model.ProgressStatuses.Phase2Tests
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase2TestRepository extends OnlineTestRepository {
  this: ReactiveRepository[_, _] =>

  type T = Phase2TestProfile
  type U = CubiksTest
}

class Phase2TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase2TestProfile, BSONObjectID]("application", mongo,
    model.persisted.Phase2TestProfile.phase2TestProfileFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase2TestRepository {

  val phaseName = "PHASE2"
  val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE2_TESTS
  val dateTimeFactory = dateTime
  val testProgress = Phase2Tests

  import repositories.BSONDateTimeHandler
  val testBsonHandler = Macros.handler[T]

  override def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE1_TESTS_PASSED),
      BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map(repositories.bsonDocToOnlineTestApplication))
  }

}
