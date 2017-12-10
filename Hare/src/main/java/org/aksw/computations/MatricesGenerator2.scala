package org.aksw.computations

import scala.collection.immutable.ListMap
import org.apache.spark.sql.SparkSession
import org.apache.spark.mllib.linalg.distributed.MatrixEntry

object MatricesGenerator2 {
  
//  /home/gsjunior/Downloads
//  var sourcePath = "src/main/resources/sample_triples.nt"
  var sourcePath = "/home/gsjunior/Documentos/datasets/airports.nt"
  var w_dest = "src/main/resources/matrices/sample_triples/w.txt"
  var f_dest = "src/main/resources/matrices/sample_triples/f.txt"
  var edges_triples_dest = "src/main/resources/matrices/sample_triples/edges_triples.txt"
  var edges_resources_dest = "src/main/resources/matrices/sample_triples/edges_resources.txt"
  
  def main(args: Array[String]): Unit = {
    
    val spark = SparkSession
      .builder()
      .appName("MatrixGenerator")
      .master("local[*]")
      .getOrCreate()
      
    import spark.implicits._
    
    
    val sc = spark.sparkContext

    val rdd = sc.textFile(sourcePath)
    
    //Creating rdd's for nodes
    val nodes_triples_rdd = rdd.map(x => x.replaceAll("\\s+", "-"))
    
    val nodes_subject_rdd = rdd.map(x => x.split("\\s+")(0))
    val nodes_predicate_rdd = rdd.map(x => x.split("\\s+")(1))
    val nodes_object_rdd = rdd.map(x => x.split("\\s+")(2))
    
    
    //Creating rdd's for edges
    val edges_subject_rdd = rdd.map(x => (x.replaceAll("\\s+", "-"),x.split("\\s+")(0)))
    val edges_predicate_rdd = rdd.map(x => (x.replaceAll("\\s+", "-"),x.split("\\s+")(1)))
    val edges_object_rdd = rdd.map(x => (x.replaceAll("\\s+", "-"),x.split("\\s+")(2)))
    
    
    // Creating rdd's for nodes and edges
    val nodes_rdd = nodes_triples_rdd.union(nodes_subject_rdd)
                    .union(nodes_predicate_rdd)
                    .union(nodes_object_rdd)
                    .distinct().zipWithIndex().toDF("node","id")
    
                    
    val edges_rdd = edges_subject_rdd
                    .union(edges_predicate_rdd)
                    .union(edges_object_rdd).toDF("src","dest")
                    
    var edges_df = edges_rdd.join(nodes_rdd,nodes_rdd.col("node") === edges_rdd.col("src")).withColumnRenamed("id", "id2").drop("node")
    edges_df = edges_df.join(nodes_rdd,nodes_rdd.col("node") === edges_rdd.col("dest")).drop("node").drop("src").drop("dest")
    edges_df = edges_df.withColumnRenamed("id2", "src").withColumnRenamed("id", "dest")
    
    var final_matrix = edges_df.rdd.map(f => (f.toSeq(0).toString(),f.toSeq(1).toString()))
                
        
     
     val triples_rdd = nodes_rdd.rdd.map{ x=>
       if (x.toSeq(0).toString().split("-").size == 3 )
       {(x.toSeq(0).toString(),x.toSeq(1).toString())} else null }.filter(x=> x!=null)
       
     val entities_rdd = nodes_rdd.rdd.map{ x=>
       if (x.toSeq(0).toString().split("-").size == 1 )
       {(x.toSeq(0).toString(),x.toSeq(1).toString()) } else null }.filter(x=> x!=null)

                    
     
     val map_edges_triples = final_matrix.groupBy(x => x._1).sortByKey(true).zipWithIndex()
     val map_edges_resources = final_matrix.groupBy(x => x._2).sortByKey(true).zipWithIndex()
     

     val count_triples = map_edges_triples.count
     val count_resources = map_edges_resources.count
     

     

       
     val w_rdd = map_edges_triples.map{
               x=>     
               val p = 1.0 / x._1._2.size.toDouble

               val values = x._1._2.toArray
               
               val me = new Array[MatrixEntry](values.size)
              for(a<- 0 to values.size-1){
                     val matrixEntry = new MatrixEntry(values(a)._1.toLong,values(a)._2.toLong,p)
                     me(a) = matrixEntry
                   }
                 
               

               me
         }.flatMap(f => f).filter(f => f != null)
     

     val f_rdd = map_edges_resources.map{
               x=>     
               val p = 1.0 / x._1._2.size.toDouble

               val values = x._1._2.toArray
               
               val me = new Array[MatrixEntry](values.size)
              for(a<- 0 to values.size-1){
                     val matrixEntry = new MatrixEntry(values(a)._2.toLong,values(a)._1.toLong,p)
                     me(a) = matrixEntry
                   }
                 
               

               me
         }.flatMap(f => f).filter(f => f != null)
               
      val w = w_rdd.map{ x => x.i + "," + x.j + "," + x.value}
      val f = f_rdd.map{ x => x.i + "," + x.j + "," + x.value}
       
           w.repartition(1).saveAsTextFile(w_dest)
           f.repartition(1).saveAsTextFile(f_dest)
           
      val map_triples = map_edges_triples.map(f => f._1._1 + "," + f._2)
      val map_resources = map_edges_resources.map(f => f._1._1 + "," + f._2)
           
           map_triples.repartition(1).saveAsTextFile(edges_triples_dest)
           map_resources.repartition(1).saveAsTextFile(edges_resources_dest)
  }
  
}