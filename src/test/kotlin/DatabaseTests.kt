import com.moshbit.katerbase.*
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import util.addYears
import util.forEachAsync
import java.util.*

class DatabaseTests {

  class EnumMongoPayload(val value1: Enum1 = Enum1.VALUE1) : MongoMainEntry() {
    enum class Enum1 {
      VALUE1, VALUE2, VALUE3
    }

    var enumList: List<Enum1> = emptyList()
    var enumSet: Set<Enum1> = emptySet()
    var enumMap1: Map<Enum1, Int> = emptyMap()
    var enumMap2: Map<Enum1, Enum1> = emptyMap()
    var date = Date()
    var long = 0L
    var stringList: List<String> = emptyList()
    val computedProp get() = value1 == Enum1.VALUE1
    val staticProp = value1 == Enum1.VALUE1
    var double = 0.0
    var map: Map<String, String> = mapOf()
    var byteArray: ByteArray = "yolo".toByteArray()
    var dateArray = emptyList<Date>()
  }

  class SimpleMongoPayload : MongoMainEntry() {
    var double = 3.0
    var string = ""
    var stringList: List<String> = emptyList()
  }

  class NullableSimpleMongoPayload : MongoMainEntry() {
    var double: Double? = null
    var string: String? = null
    var stringList: List<String?>? = null
  }

  @Test
  fun enumHandling1() {
    val payload = EnumMongoPayload().apply { _id = "testId" }
    testDb.getCollection<EnumMongoPayload>().insertOne(payload, upsert = true)

    val results = testDb.getCollection<EnumMongoPayload>().find(EnumMongoPayload::value1 equal EnumMongoPayload.Enum1.VALUE1)
    assert(results.toList().isNotEmpty())
    testDb.getCollection<EnumMongoPayload>().deleteOne(EnumMongoPayload::_id equal "testId")
  }

  @Test
  fun enumHandling2() {
    val payload = EnumMongoPayload().apply { _id = "testId" }
    testDb.getCollection<EnumMongoPayload>().insertOne(payload, upsert = true)

    testDb.getCollection<EnumMongoPayload>().updateOne(EnumMongoPayload::_id equal "testId") {
      EnumMongoPayload::value1 setTo EnumMongoPayload.Enum1.VALUE2
    }

    val results = testDb.getCollection<EnumMongoPayload>().find(EnumMongoPayload::value1 equal EnumMongoPayload.Enum1.VALUE2)
    assert(results.toList().isNotEmpty())

    testDb.getCollection<EnumMongoPayload>().deleteOne(EnumMongoPayload::_id equal "testId")
  }

  @Test
  fun faultyEnumList() {
    // Katerbase will print those 2 warnings on stdout:
    // Array enumList in EnumMongoPayload contains null, but is a non-nullable collection: _id=faultyEnumList
    // Enum value FAULTY of type Enum1 doesn't exists any more but still present in database: EnumMongoPayload, _id=faultyEnumList

    testDb.getCollection<EnumMongoPayload>().deleteOne(EnumMongoPayload::_id equal "faultyEnumList")
    testDb.getCollection<EnumMongoPayload>().internalCollection.insertOne(
      Document(
        listOf(
          "_id" to "faultyEnumList",
          "enumList" to listOf(
            "VALUE1", "FAULTY", null, "VALUE3"
          )
        ).toMap()
      )
    )

    val result = testDb.getCollection<EnumMongoPayload>().findOne(EnumMongoPayload::_id equal "faultyEnumList")

    assertEquals(2, result!!.enumList.size)
    assertEquals(EnumMongoPayload.Enum1.VALUE1, result.enumList[0])
    assertEquals(EnumMongoPayload.Enum1.VALUE3, result.enumList[1])
  }

  @Test
  fun faultyEnumSet() {
    // Katerbase will print those 2 warnings on stdout:
    // Array enumSet in EnumMongoPayload contains null, but is a non-nullable collection: _id=faultyEnumSet
    // Enum value FAULTY of type Enum1 doesn't exists any more but still present in database: EnumMongoPayload, _id=faultyEnumSet

    testDb.getCollection<EnumMongoPayload>().deleteOne(EnumMongoPayload::_id equal "faultyEnumSet")
    testDb.getCollection<EnumMongoPayload>().internalCollection.insertOne(
      Document(
        setOf(
          "_id" to "faultyEnumSet",
          "enumSet" to setOf(
            "VALUE1", "FAULTY", null, "VALUE3"
          )
        ).toMap()
      )
    )

    val result = testDb.getCollection<EnumMongoPayload>().findOne(EnumMongoPayload::_id equal "faultyEnumSet")

    assertEquals(2, result!!.enumSet.size)

    val enumListSorted = result.enumSet.toList().sorted()

    assertEquals(2, enumListSorted.size)
    assertEquals(EnumMongoPayload.Enum1.VALUE1, enumListSorted[0])
    assertEquals(EnumMongoPayload.Enum1.VALUE3, enumListSorted[1])
  }

  @Test
  fun dateDeserialization() {
    val payload = EnumMongoPayload().apply { _id = "datetest" }
    testDb.getCollection<EnumMongoPayload>().let { coll ->
      coll.insertOne(payload, upsert = true)
      val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
      assert(res.date.toString() == payload.date.toString())
    }
  }

  @Test
  fun customByteArrayDeserialization1() {
    val payload = EnumMongoPayload().apply { _id = "bytetest" }
    testDb.getCollection<EnumMongoPayload>().let { coll ->
      coll.insertOne(payload, upsert = true)
      val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
      assertEquals(String(payload.byteArray), String(res.byteArray))
      assertEquals(payload.byteArray.size, res.byteArray.size)
    }
  }

  @Test
  fun customByteArrayDeserialization2() {
    val payload = EnumMongoPayload().apply { _id = "bytetest"; byteArray = "yo 😍 \u0000 😄".toByteArray() }
    testDb.getCollection<EnumMongoPayload>().let { coll ->
      coll.insertOne(payload, upsert = true)
      val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
      assertEquals(String(payload.byteArray), String(res.byteArray))
      assertEquals(payload.byteArray.size, res.byteArray.size)
    }
  }

  class CustomDateArrayCLass {
    val array = listOf(Date(), Date().addYears(-1), Date().addYears(-10))
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun customDateArrayTest() {
    val payload = CustomDateArrayCLass()
    val bson = JsonHandler.toBsonDocument(payload)
    val newPayload = JsonHandler.fromBson(bson, CustomDateArrayCLass::class)

    (payload.array zip (bson["array"] as List<Date>)).forEach { (old, new) -> assert(old == new) }
    (payload.array zip newPayload.array).forEach { (old, new) -> assert(old == new) }
  }

  @Test
  fun multithreadedFindOneOrInsert() {
    val id = "multicreateid"
    val value = EnumMongoPayload.Enum1.VALUE2
    var createNewCalls = 0
    testDb.getCollection<EnumMongoPayload>().drop()
    (1..50).forEachAsync {
      (1..500).forEach {
        val result = testDb.getCollection<EnumMongoPayload>().findOneOrInsert(EnumMongoPayload::_id equal id) {
          createNewCalls++
          EnumMongoPayload(value1 = value)
        }
        assert(result._id == id)
        assert(result.value1 == value)
      }
    }
    println("Called create $createNewCalls times")
  }

  @Test
  fun primitiveList() {
    val id = "primitiveTest"
    val payload = EnumMongoPayload().apply { stringList = listOf("a", "b"); _id = id }

    testDb.getCollection<EnumMongoPayload>().insertOne(payload, upsert = true)
    var retrievedPayload = testDb.getCollection<EnumMongoPayload>().findOne(EnumMongoPayload::_id equal id)!!
    assert(payload.stringList == retrievedPayload.stringList)

    testDb.getCollection<EnumMongoPayload>().updateOne(EnumMongoPayload::_id equal id) {
      EnumMongoPayload::stringList setTo listOf("c", "d")
    }
    retrievedPayload = testDb.getCollection<EnumMongoPayload>().findOne(EnumMongoPayload::_id equal id)!!

    assert(listOf("c", "d") == retrievedPayload.stringList)
  }

  @Test
  fun persistencyTest() {
    val range = (1..10000)
    val collection = testDb.getCollection<EnumMongoPayload>()
    val idPrefix = "persistencyTest"

    range.forEach { index ->
      val id = idPrefix + index
      collection.deleteOne(EnumMongoPayload::_id equal id)
    }

    range.forEachAsync { index ->
      val id = idPrefix + index
      collection.deleteOne(EnumMongoPayload::_id equal id)
      collection.insertOne(EnumMongoPayload().apply { _id = id }, upsert = false)
      assert(collection.findOne(EnumMongoPayload::_id equal id) != null)
      collection.deleteOne(EnumMongoPayload::_id equal id)
    }

    range.forEachAsync { index ->
      val id = idPrefix + index
      collection.deleteOne(EnumMongoPayload::_id equal id)
      collection.findOneOrInsert(EnumMongoPayload::_id equal id) { EnumMongoPayload() }
      assert(collection.findOne(EnumMongoPayload::_id equal id) != null)
      collection.deleteOne(EnumMongoPayload::_id equal id)
    }
  }

  @Test
  fun computedPropTest() {
    val payload = EnumMongoPayload()
    val bson = payload.toBSONDocument()
    assert(bson["computedProp"] == null)
    assertEquals(14, bson.size)
  }
/*
  @Test
  fun nullListTest() {
    val raw = """{"_id":"","value1":"VALUE1","enumList":[],"date":"2017-08-23T14:52:30.252+02","stringList":["test1", null, "test2"],"staticProp":true,"computedProp":true}"""
    val payload: EnumMongoPayload = JsonHandler.fromJson(raw)
    assert(payload.stringList.size == 2)
  }*/

  @Test
  fun testInfinity() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { drop() }
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityA"; double = Double.POSITIVE_INFINITY }, upsert = false)
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityB"; double = Double.MAX_VALUE }, upsert = false)
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityC"; double = Double.MIN_VALUE }, upsert = false)
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityD"; double = 0.0 }, upsert = false)
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityE"; double = Double.NEGATIVE_INFINITY }, upsert = false)
    collection.insertOne(EnumMongoPayload().apply { _id = "testInfinityF"; double = Double.NaN }, upsert = false)

    assert(collection.find().count() == 6)
    assert(collection.find(EnumMongoPayload::double equal Double.POSITIVE_INFINITY).count() == 1)
    assert(collection.find(EnumMongoPayload::double equal Double.MAX_VALUE).count() == 1)
    assert(collection.find(EnumMongoPayload::double equal Double.MIN_VALUE).count() == 1)
    assert(collection.find(EnumMongoPayload::double equal 0.0).count() == 1)
    assert(collection.find(EnumMongoPayload::double equal Double.NEGATIVE_INFINITY).count() == 1)
    assert(collection.find(EnumMongoPayload::double equal Double.NaN).count() == 1)

    assert(collection.find(EnumMongoPayload::double lowerEquals Double.POSITIVE_INFINITY).count() == 5)
    assert(collection.find(EnumMongoPayload::double lower Double.POSITIVE_INFINITY).count() == 4)
    assert(collection.find(EnumMongoPayload::double lowerEquals Double.MAX_VALUE).count() == 4)
    assert(collection.find(EnumMongoPayload::double lower Double.MAX_VALUE).count() == 3)
    assert(collection.find(EnumMongoPayload::double lower 1000.0).count() == 3)
    assert(collection.find(EnumMongoPayload::double lowerEquals Double.MIN_VALUE).count() == 3)
    assert(collection.find(EnumMongoPayload::double lower Double.MIN_VALUE).count() == 2)
    assert(collection.find(EnumMongoPayload::double lowerEquals 0.0).count() == 2)
    assert(collection.find(EnumMongoPayload::double lower 0.0).count() == 1)
    assert(collection.find(EnumMongoPayload::double lower -1000.0).count() == 1)
    assert(collection.find(EnumMongoPayload::double lowerEquals Double.NEGATIVE_INFINITY).count() == 1)
    assert(collection.find(EnumMongoPayload::double lower Double.NEGATIVE_INFINITY).count() == 0)

    assert(collection.find(EnumMongoPayload::double greater Double.POSITIVE_INFINITY).count() == 0)
    assert(collection.find(EnumMongoPayload::double greaterEquals Double.POSITIVE_INFINITY).count() == 1)
    assert(collection.find(EnumMongoPayload::double greater Double.MAX_VALUE).count() == 1)
    assert(collection.find(EnumMongoPayload::double greaterEquals Double.MAX_VALUE).count() == 2)
    assert(collection.find(EnumMongoPayload::double greater 1000.0).count() == 2)
    assert(collection.find(EnumMongoPayload::double greater Double.MIN_VALUE).count() == 2)
    assert(collection.find(EnumMongoPayload::double greaterEquals Double.MIN_VALUE).count() == 3)
    assert(collection.find(EnumMongoPayload::double greater 0.0).count() == 3)
    assert(collection.find(EnumMongoPayload::double greaterEquals 0.0).count() == 4)
    assert(collection.find(EnumMongoPayload::double greater -1000.0).count() == 4)
    assert(collection.find(EnumMongoPayload::double greater Double.NEGATIVE_INFINITY).count() == 4)
    assert(collection.find(EnumMongoPayload::double greaterEquals Double.NEGATIVE_INFINITY).count() == 5)
  }

  @Test
  fun unsetTest() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { drop() }
    val id = "unsetTest"
    collection.insertOne(document = EnumMongoPayload().apply { _id = id }, upsert = false)

    fun put(key: String) = collection.updateOne(EnumMongoPayload::_id equal id) {
      EnumMongoPayload::map.child(key) setTo key
    }

    fun remove(key: String) = collection.updateOne(EnumMongoPayload::_id equal id) {
      EnumMongoPayload::map.child(key).unset()
    }

    fun get() = collection.findOne(EnumMongoPayload::_id equal id)!!.map

    assert(get().isEmpty())

    (1..10).forEach { put(it.toString()) }
    get().let { map -> (1..10).map { it.toString() }.forEach { assert(map[it] == it) } }

    (1..5).forEach { remove(it.toString()) }
    get().let { map -> (6..10).map { it.toString() }.forEach { assert(map[it] == it) } }

    collection.updateOne(EnumMongoPayload::_id equal id) {
      EnumMongoPayload::map.unset()
    }

    assert(get().isEmpty())
  }

  @Test
  fun distinctTest() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { drop() }
    val id = "distinctTest"

    (0..100).forEach { index ->
      collection.insertOne(EnumMongoPayload().apply {
        _id = "$id-$index-first"
        this.double = index.toDouble()
      }, upsert = false)
      collection.insertOne(EnumMongoPayload().apply {
        _id = "$id-$index-second"
        this.double = index.toDouble()
      }, upsert = false)
    }

    val distinctValues = collection.distinct(EnumMongoPayload::double).toList()

    assert(distinctValues.distinct().count() == distinctValues.count())
  }

  @Test
  fun equalsTest() {
    val collection1 = testDb.getCollection<EnumMongoPayload>().apply { drop() }
    val collection2 = testDb.getCollection<SimpleMongoPayload>().apply { drop() }


    // Find
    val cursor1 = collection1.find()
    val cursor1b = collection1.find()
    val cursor2 = collection2.find()

    // Different collection
    assert(cursor1 != cursor2)
    assert(cursor1.hashCode() != cursor2.hashCode())

    // Same collection, same cursor
    assert(cursor1 == cursor1b)
    assert(cursor1.hashCode() == cursor1b.hashCode())


    // Distinct
    val distinct1 = collection1.distinct(EnumMongoPayload::double)
    //val distinct1b = collection1.distinct(EnumMongoPayload::double)
    val distinct2 = collection2.distinct(SimpleMongoPayload::double)

    // Different collection
    assert(distinct1 != distinct2)
    assert(distinct1.hashCode() != distinct2.hashCode())

    // We don't have equals/hashCode for distinct
    //assert(distinct1 == distinct1b)
    //assert(distinct1.hashCode() == distinct1b.hashCode())
  }

  @Test
  fun longTest() {
    val payload = EnumMongoPayload().apply { _id = "longTest" }

    // 0
    (-100L..100L).forEach { long ->
      testDb.getCollection<EnumMongoPayload>().let { coll ->
        payload.long = long
        coll.insertOne(payload, upsert = true)
        val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
        assertEquals(long, res.long)
      }
    }

    // INT_MIN
    (Int.MIN_VALUE.toLong() - 100L..Int.MIN_VALUE.toLong() + 100L).forEach { long ->
      testDb.getCollection<EnumMongoPayload>().let { coll ->
        payload.long = long
        coll.insertOne(payload, upsert = true)
        val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
        assertEquals(long, res.long)
      }
    }

    // INT_MAX
    (Int.MAX_VALUE.toLong() - 100L..Int.MAX_VALUE.toLong() + 100L).forEach { long ->
      testDb.getCollection<EnumMongoPayload>().let { coll ->
        payload.long = long
        coll.insertOne(payload, upsert = true)
        val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
        assertEquals(long, res.long)
      }
    }

    // LONG_MIN
    (Long.MIN_VALUE..Long.MIN_VALUE + 100L).forEach { long ->
      testDb.getCollection<EnumMongoPayload>().let { coll ->
        payload.long = long
        coll.insertOne(payload, upsert = true)
        val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
        assertEquals(long, res.long)
      }
    }

    // LONG_MAX
    (Long.MAX_VALUE - 100L..Long.MAX_VALUE).forEach { long ->
      testDb.getCollection<EnumMongoPayload>().let { coll ->
        payload.long = long
        coll.insertOne(payload, upsert = true)
        val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
        assertEquals(long, res.long)
      }
    }
  }

  @Test
  fun dateArrayTest() {
    val payload = EnumMongoPayload().apply { _id = "dateArrayTest" }

    testDb.getCollection<EnumMongoPayload>().let { coll ->
      payload.dateArray = listOf(Date(), Date().addYears(1), Date().addYears(10))
      coll.insertOne(payload, upsert = true)
      val res = coll.findOne(EnumMongoPayload::_id equal payload._id)!!
      assertEquals(payload.dateArray[0], res.dateArray[0])
      assertEquals(payload.dateArray[1], res.dateArray[1])
      assertEquals(payload.dateArray[2], res.dateArray[2])
      assertEquals(payload.dateArray.size, res.dateArray.size)
    }
  }

  @Test
  fun hintTest() {
    testDb.getCollection<EnumMongoPayload>()
      .find(EnumMongoPayload::value1 equal EnumMongoPayload.Enum1.VALUE1)
      .hint("value1_1_date_1")
  }

  @Test
  fun invalidHintTest() {
    assertThrows(IllegalArgumentException::class.java) {
      testDb.getCollection<EnumMongoPayload>()
        .find(EnumMongoPayload::value1 equal EnumMongoPayload.Enum1.VALUE1)
        .hint("value1_1_date_-1")
    }
  }

  @Test
  fun nullableTest() {
    val payload = NullableSimpleMongoPayload().apply { _id = "nullableTest" }
    testDb.getCollection<NullableSimpleMongoPayload>().insertOne(payload, upsert = true)
    assertNotNull(testDb.getCollection<NullableSimpleMongoPayload>().findOne(NullableSimpleMongoPayload::_id equal "nullableTest"))
    val simpleMongoPayload = testDb.getCollection<SimpleMongoPayload>().findOne(SimpleMongoPayload::_id equal "nullableTest")!!

    // Known limitation: SimpleMongoPayload.string is not nullable, but due to the Jackson deserialization we throw a NPE on access.
    try {
      println(simpleMongoPayload.string.length)
      assert(false)
    } catch (e: NullPointerException) {
      assert(true)
    }

    testDb.getCollection<NullableSimpleMongoPayload>().deleteOne(NullableSimpleMongoPayload::_id equal "testId")
  }

  @Test
  fun findOneOrInsertTest() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { clear() }

    val payload = EnumMongoPayload().apply { long = 69 }
    var returnVal = collection.findOneOrInsert(EnumMongoPayload::_id equal "findOneOrInsertTest", newEntry = { payload })
    assertEquals(payload.long, returnVal.long)

    collection.updateOne(EnumMongoPayload::_id equal "findOneOrInsertTest") {
      EnumMongoPayload::long incrementBy 1
    }

    returnVal = collection.findOneOrInsert(EnumMongoPayload::_id equal "findOneOrInsertTest", newEntry = { payload })
    assertEquals(payload.long + 1, returnVal.long)
  }

  @Test
  fun suspendingFindTest() = runBlocking {
    val collection = testDb.getSuspendingCollection<EnumMongoPayload>().apply { clear() }

    val payloads = (1..50)
      .map {
        EnumMongoPayload().apply {
          _id = randomId()
          long = it.toLong()
        }
      }
      .onEach { collection.insertOne(it, upsert = false) }

    collection.find().collect { payload ->
      assert(payloads.any { it._id == payload._id })
    }
  }

  @Test
  fun enumMaps() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { drop() }
    val id = "enumMaps"

    // Insert payload
    val insertPayload = EnumMongoPayload().apply {
      _id = id
      enumMap1 = mapOf(EnumMongoPayload.Enum1.VALUE2 to 2)
      enumMap2 = mapOf(EnumMongoPayload.Enum1.VALUE3 to EnumMongoPayload.Enum1.VALUE2)
    }
    collection.insertOne(insertPayload, upsert = false)

    collection.findOne(EnumMongoPayload::_id equal id)!!.apply {
      assertEquals(insertPayload.enumMap1, enumMap1)
      assertEquals(insertPayload.enumMap2, enumMap2)
    }

    // Change fields
    collection.updateOne(EnumMongoPayload::_id equal id) {
      EnumMongoPayload::enumMap1 setTo mapOf(EnumMongoPayload.Enum1.VALUE1 to 1)
      EnumMongoPayload::enumMap2 setTo mapOf(EnumMongoPayload.Enum1.VALUE2 to EnumMongoPayload.Enum1.VALUE3)
    }

    collection.findOne(EnumMongoPayload::_id equal id)!!.apply {
      assertEquals(mapOf(EnumMongoPayload.Enum1.VALUE1 to 1), enumMap1)
      assertEquals(mapOf(EnumMongoPayload.Enum1.VALUE2 to EnumMongoPayload.Enum1.VALUE3), enumMap2)
    }
  }

  @Test
  fun queryStats() {
    val collection = testDb.getCollection<EnumMongoPayload>().apply { drop() }

    (1..100)
      .map { EnumMongoPayload().apply { _id = randomId() } }
      .forEach { collection.insertOne(it, upsert = false) }

    val stats = collection.getQueryStats()

    assertEquals(true, stats.executionSuccess)
    assertEquals(100, stats.returnedDocuments)
  }

  @Test
  fun dbStats() {
    val stats = testDb.getDatabaseStats()
    assert(stats.collections > 0)
  }

  @Test
  fun indexOperatorCheck() {
    with(MongoDatabaseDefinition.Collection(EnumMongoPayload::class, "enumColl", collectionSizeCap = null)) {
      index(
        EnumMongoPayload::double.ascending(), partialIndex = arrayOf(
          EnumMongoPayload::double greater 0
        )
      )
      index(
        EnumMongoPayload::double.ascending(), partialIndex = arrayOf(
          EnumMongoPayload::double equal 0
        )
      )
      index(
        EnumMongoPayload::double.ascending(), partialIndex = arrayOf(
          EnumMongoPayload::double exists true
        )
      )

      assertThrows(IllegalArgumentException::class.java) {
        index(
          EnumMongoPayload::double.ascending(), partialIndex = arrayOf(
            EnumMongoPayload::double notEqual 0
          )
        )
      }
      assertThrows(IllegalArgumentException::class.java) {
        index(
          EnumMongoPayload::double.ascending(), partialIndex = arrayOf(
            EnumMongoPayload::double exists false
          )
        )
      }
    }
  }

  companion object {
    lateinit var testDb: MongoDatabase

    @Suppress("unused")
    @BeforeAll
    @JvmStatic
    fun setup() {
      testDb = MongoDatabase("mongodb://localhost:27017/local") {
        collection<EnumMongoPayload>("enumColl") {
          index(EnumMongoPayload::value1.ascending())
          index(EnumMongoPayload::value1.ascending(), EnumMongoPayload::date.ascending())
        }
        collection<SimpleMongoPayload>("simpleMongoColl")
        collection<NullableSimpleMongoPayload>("simpleMongoColl") // Use the same underlying mongoDb collection as SimpleMongoPayload
      }
    }
  }
}