package org.dyne.danielsan.openblockchain.gen

import com.datastax.spark.connector._
import org.apache.spark.SparkContext

object Signals extends Helpers {

  def allOrNor(granularity: String)(implicit sc: SparkContext): List[Map[String, Long]] = {
    sc.cassandraTable[(Long, List[String])]("openblockchain", "transactions")
      .select("blocktime", "vout")
      .map {
        case (time, voutList) =>
          val countAll = voutList.length.toLong
          val countOpReturn = voutList.count(_.contains("OP_RETURN")).toLong
          val countNonOpReturn = voutList.count(!_.contains("OP_RETURN")).toLong
          (floorTimestamp(time, granularity), (countAll, countOpReturn, countNonOpReturn))
      }
      .reduceByKey {
        case (v1, v2) =>
          (v1._1 + v2._1, v1._2 + v2._2, v1._3 + v2._3)
      }
      .sortBy(_._1)
      .map {
        case (time, values) =>
          Map[String, Long](
            "x" -> time,
            "all" -> values._1,
            "op_return" -> values._2,
            "non_op_return" -> values._3
          )
      }
      .collect()
      .toList
  }

  def average(granularity: String)(implicit sc: SparkContext): Map[String, Double] = {
    val data = sc.cassandraTable[(Long, List[String])]("openblockchain", "transactions")
      .select("blocktime", "vout")
      .map {
        case (time, voutList) =>
          val countAll = voutList.length.toLong
          val countOpReturn = voutList.count(_.contains("OP_RETURN")).toLong
          val countNonOpReturn = voutList.count(!_.contains("OP_RETURN")).toLong
          (floorTimestamp(time, granularity), (countAll, countOpReturn, countNonOpReturn))
      }
      .reduceByKey {
        case (v1, v2) =>
          (v1._1 + v2._1, v1._2 + v2._2, v1._3 + v2._3)
      }
      .map {
        case (time, values) =>
          (1L, values)
      }
      .reduce {
        case ((t1, v1), (t2, v2)) =>
          (t1 + t2, (v1._1 + v2._1, v1._2 + v2._2, v1._3 + v2._3))
      }

    val (days, (countAll, countOpReturn, countNonOpReturn)) = data

    Map[String, Double](
      s"avg_all_signals_per_$granularity" -> countAll.toDouble / days,
      s"avg_op_return_signals_per_$granularity" -> countOpReturn.toDouble / days,
      s"avg_non_op_return_signals_per_$granularity" -> countNonOpReturn.toDouble / days
    )
  }

}
