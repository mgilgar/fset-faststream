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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model._
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object SecondPhase1ReminderExpiringTestJob extends SecondReminderExpiringTestJob {
  override val service = Phase1TestService
  override val reminderNotice: ReminderNotice = Phase1SecondReminder
  val config = SecondPhase1ReminderExpiringTestJobConfig
}

object SecondPhase2ReminderExpiringTestJob extends SecondReminderExpiringTestJob {
  override val service = Phase2TestService
  override val reminderNotice: ReminderNotice = Phase2SecondReminder
  val config = SecondPhase2ReminderExpiringTestJobConfig
}

object SecondPhase3ReminderExpiringTestJob extends SecondReminderExpiringTestJob {
  override val service = Phase3TestService
  override val reminderNotice: ReminderNotice = Phase3SecondReminder
  val config = SecondPhase3ReminderExpiringTestJobConfig
}

trait SecondReminderExpiringTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val reminderNotice: ReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = new HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}

object SecondPhase1ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.second-phase1-reminder-expiring-test-job",
  name = "SecondPhase1ReminderExpiringTestJob"
)

object SecondPhase2ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.second-phase2-reminder-expiring-test-job",
  name = "SecondPhase2ReminderExpiringTestJob"
)

object SecondPhase3ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.second-phase3-reminder-expiring-test-job",
  name = "SecondPhase3ReminderExpiringTestJob"
)
