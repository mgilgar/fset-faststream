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

package services.events

import common.FutureEx
import factories.UUIDFactory
import model.persisted.eventschedules.{ Event, EventType, VenueType }
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import play.api.Play
import resource._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Try }

object EventsParsingService extends EventsParsingService {
  override def fileContents: Future[List[String]] = Future.successful {
    val input = managed(Play.current.resourceAsStream("fset-faststream-event-schedule.csv").get)
    input.acquireAndGet(file => scala.io.Source.fromInputStream(file).getLines().toList.tail)
  }
}

trait EventsParsingService {

  private val skillsIdxTable = List(
    "ASSESSOR" -> 10,
    "CHAIR" -> 11,
    "DEPARTMENTAL_ASSESSOR" -> 12,
    "EXERCISE_MARKER" -> 13,
    "QUALITY_ASSURANCE_COORDINATOR" -> 14
  )

  def fileContents: Future[List[String]]

  private def stringToRequirement(strRequirement: String): Int =
    strRequirement match {
      case s if s.isEmpty => 0 // no value means 0 requirement
      case s => s.toInt
    }

  def processCentres(): Future[List[Event]] = {

    fileContents.flatMap { centres =>

      FutureEx.traverseSerial(centres.zipWithIndex) {
        case (line, idx) =>
          val tryRes = Try {
            event(line)
          }.recoverWith {
            case ex =>
              Failure(new Exception(s"Error on L${idx + 1} of the CSV. ${ex.getMessage}. ${ex.getClass.getCanonicalName}"))
          }
          Future.fromTry(tryRes)
      }
    }
  }

  lazy val df = DateTimeFormat.forPattern("HH:mm")

  private def event(items: String): Event = {
    event(items.split(", ?", -1))
  }

  private def event(items: Array[String]): Event = {
    val eventType = EventType.withName(items.head.replaceAll("\\s|-", "_").toUpperCase)
    val eventDescription = items(1)
    val location = items(2)
    val venue = VenueType.withName(items(3).replaceAll("\\s|-", "_").toUpperCase)
    val date = LocalDate.parse(items(4), DateTimeFormat.forPattern("dd/MM/yy"))
    val startTime = df.parseLocalTime(items(5))
    val endTime = df.parseLocalTime(items(6))
    val capacity = items(7).toInt
    val minViableAttendees = items(8).toInt
    val attendeeSafetyMargin = items(9).toInt

    if(eventDescription.length > 10) throw new Exception("Event description cannot be more than 10 characters")

    val skillRequirements: Map[String, Int] =
      skillsIdxTable.map {
        case (skill, skillIdx) => skill -> stringToRequirement(items(skillIdx))
      }.toMap

    Event(
      id = UUIDFactory.generateUUID(),
      eventType = eventType,
      eventDescription = eventDescription,
      location = location,
      venue = venue,
      date = date,
      startTime = startTime,
      endTime = endTime,
      capacity = capacity,
      minViableAttendees = minViableAttendees,
      attendeeSafetyMargin = attendeeSafetyMargin,
      skillRequirements = skillRequirements
    )
  }

}
