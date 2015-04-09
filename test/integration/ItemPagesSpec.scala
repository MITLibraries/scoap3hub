import org.specs2.mutable._
import org.specs2.runner._

import play.api.test._
import play.api.test.Helpers._
import org.fest.assertions.Assertions.assertThat
import play.api.Application
import play.api.Play
import play.api.Play.current
import models.{ Collection, ContentType, Item, Publisher, ResourceMap, Scheme, Subscriber, Topic, User }

/**
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class ItemPagesSpec extends Specification {

  def create_user(role: String) = User.make("bob", "bob@example.com", role, "current_user")
  def item_factory(count: Int) {
    val ct = ContentType.make("tag", "label", "desc", Some("logo"))
    val rm = ResourceMap.make("rm_tag", "rm_desc", Some("http://www.example.com"))
    val user = User.make("pubuser", "pubuser@example.com", "", "pub_identity")
    val pub = Publisher.make(user.id, "pubtag", "pubname", "pubdesc", "pubcat", "pubstatus",
                             Some(""), Some(""))
    val col = Collection.make(pub.id, ct.id, rm.id, "coll1_tag", "coll1 desc", "open")
    1 to count foreach { n => Item.make(col.id, ct.id, "location", "abc:" + n) }
  }

  "Item pages" should {
    "as an unauthenticated User" should {

      // GET /items/browse
      "browsing items works" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        item_factory(11)
        Item.all.size must equalTo(11)
        browser.goTo("http://localhost:" + port + "/items/browse?filter=collection&id=1")
        assertThat(browser.title()).isEqualTo("Item Browse - TopicHub")
        browser.pageSource must contain("Viewing 1 - 10 of 11")
        browser.$("#next_page").click
        browser.pageSource must contain("Viewing 11 - 11 of 11")
        browser.$("#prev_page").click
        browser.pageSource must contain("Viewing 1 - 10 of 11")
      }

      // GET /item/:id
      "accessing an item works" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        item_factory(1)
        browser.goTo("http://localhost:" + port + "/item/1")
        assertThat(browser.title()).isEqualTo("Item - TopicHub")
        browser.pageSource must contain("View METS »")
        browser.pageSource must contain("Download »")
        browser.pageSource must contain("Deposit »")
      }

      // GET /item/package/:id
      "generating an item package works" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        skipped("Need to consider how to actually test this.")
      }

      // GET /item/deposit/:id
      "depositing an item redirects to login" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        item_factory(1)
        val action = route(FakeRequest(GET, "/item/deposit/1")).get
        redirectLocation(action) must beSome.which(_ == "/login")
      }

      // GET /item/mets/:id
      "generating mets works" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        item_factory(1)
        val action = route(FakeRequest(GET, "/item/mets/1")).get
        status(action) must equalTo(OK)
        contentType(action) must beSome.which(_ == "application/xml")
      }
    }

    "as a signed in user with no subscriber affiliated" should {
      // GET /item/deposit/:id
      "depositing an item redirects to error" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        val sub_user = User.make("sub", "sub@example.com", "", "sub_identity")
        Subscriber.make(sub_user.id, "Sub Name", "cat", "contact", Some("link"), Some("logo"))
        create_user("current_user")
        item_factory(1)
        val action = route(FakeRequest(GET, "/item/deposit/1")).get
        contentAsString(action) must contain ("Reason: You are not a Subscriber")
      }.pendingUntilFixed(": currently we only support one subscriber so this works. See https://github.com/MITLibraries/scoap3hub/issues/46")
    }

    "as a signed in user with a subscriber affiliated" should {
      // GET /item/deposit/:id
      "depositing an item redirects to error with no channel defined" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        val user = User.make("sub", "sub@example.com", "", "current_user")
        Subscriber.make(user.id, "Sub Name", "cat", "contact", Some("link"), Some("logo"))
        item_factory(1)
        val action = route(FakeRequest(GET, "/item/deposit/1")).get
        status(action) must throwA[RuntimeException](message = "You must define a Channel")
      }

      // GET /item/deposit/:id
      "depositing an item works with a channel defined" in new WithBrowser(
        app = FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        skipped("Need to consider how to actually test this.")
      }
    }
  }
}
