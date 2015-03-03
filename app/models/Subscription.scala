/**
  * Copyright (c) 2015 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */
package models

import java.util.Date

import play.api.db.DB
import play.api._
import play.api.Play.current

import anorm.SqlParser._
import anorm.~
import anorm.SQL
import anorm.Row

/** Subscription is a subscriber rule of action to be taken for items belonging
  * to a topic, where actions may include email notifications, SWORD delivery of
  * packages, etc
  *
  * @author richardrodgers
  */

case class Subscription(id: Int,  // DB key
                        subscriberId: Int, // DB key of owning subscriber
                        topicId: Int,   // DB key of topic subscribed to
                        action: String,  // Subscription action to take
                        created: Date,  // when subscription established
                        updated: Date,  // when last subscription action taken
                        cancelled: Date, // when, if ever, subscription cancelled
                        earliest: Date,  // effective start date for content matching
                        latest: Date,    // effective end date for content matching
                        active: Boolean) {    // is subscription currently active
  def cancel = {
    DB.withConnection { implicit c =>
      SQL("update subscription set active = false, cancelled = {cancelled} where id = {id}")
      .on('cancelled -> new Date, 'id -> id).executeUpdate()
    }
  }

  def topic = {
    DB.withConnection { implicit c =>
      SQL("select * from topic where id = {topic_id}").on('topic_id -> topicId).as(Topic.topic.singleOpt)
    }
  }

  def transferCount = {
    DB.withConnection { implicit c =>
      val count = SQL("select count(*) as c from transfer where subscription_id = {id}").on('id -> id).apply.head
      count[Long]("c")
    }
  }
}

object Subscription {
  val subscrip = {
    get[Int]("id") ~ get[Int]("subscriber_id") ~ get[Int]("topic_id") ~ get[String]("action") ~
    get[Date]("created") ~ get[Date]("updated") ~ get[Date]("cancelled") ~
    get[Date]("earliest") ~ get[Date]("latest") ~ get[Boolean]("active") map {
      case id ~ subscriberId ~ topicId ~ action ~ created ~ updated ~ cancelled ~ earliest ~ latest ~ active =>
        Subscription(id, subscriberId, topicId, action, created, updated, cancelled, earliest, latest, active)
    }
  }

  def findById(id: Int): Option[Subscription] = {
    DB.withConnection { implicit c =>
      SQL("select * from subscription where id = {id}").on('id -> id).as(subscrip.singleOpt)
    }
  }

  def withSubscriber(sid: Int): List[Subscription] = {
    DB.withConnection { implicit c =>
      SQL("select * from subscription where subscriber_id = {sid}").on('sid -> sid).as(subscrip *)
    }
  }

  def schemeCount(subscriberId: Int, schemeId: Int) = {
    DB.withConnection { implicit c =>
      val count = SQL("select count(*) as c from subscription, topic where subscription.subscriber_id = {sub_id} and subscription.topic_id = topic.id and topic.scheme_id = {sch_id}")
      .on('sub_id -> subscriberId, 'sch_id -> schemeId).apply.head
      count[Long]("c")
    }
  }

  def inScheme(subscriberId: Int, schemeId: Int, page: Int) = {
    val offset = page * 10
    DB.withConnection { implicit c =>
      SQL(
        """
          select subscription.* from subscription, topic where subscription.subscriber_id = {sub_id}
          and subscription.topic_id = topic.id and topic.scheme_id = {sch_id}
          order by created desc
          limit 10 offset {offset}
        """
      ).on('sub_id -> subscriberId, 'sch_id -> schemeId, 'offset -> offset).as(subscrip *)
    }
  }

  def create(subscriberId: Int, topicId: Int, action: String, earliest: Date, latest: Date) = {
    val created = new Date
    val updated = created
    val cancelled = created
    DB.withConnection { implicit c =>
      SQL("insert into subscription (subscriber_id, topic_id, action, created, updated, cancelled, earliest, latest, active) values ({subscriber_id}, {topic_id}, {action}, {created}, {updated}, {cancelled}, {earliest}, {latest}, {active})")
      .on('subscriber_id -> subscriberId, 'topic_id -> topicId, 'action -> action, 'created -> created, 'updated -> updated, 'cancelled -> cancelled, 'earliest -> earliest, 'latest -> latest, 'active -> true).executeInsert()
    }
  }
}
