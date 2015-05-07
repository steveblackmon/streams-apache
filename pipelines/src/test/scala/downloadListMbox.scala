def downloadFile(url: String, file: String) {
  try {
    val src = scala.io.Source.fromURL(url)
    val out = new java.io.FileWriter(file)
    out.write(src.mkString)
    out.close
  } catch {
    case e: java.io.IOException => "error occured"
  }
}

val years = List.range(2009, 2015)
val months = List.range(01, 12)

val monthsRDD = sc.parallelize(months)
val yearsRDD = sc.parallelize(years)

val partsMYRDD = yearsRDD.cartesian(monthsRDD)

val partsStringsRDD = partsMYRDD.map(x => x._1.toString + f"${x._2}%02d")

val partsUrlsRDD = partsStringsRDD.map(x => (x, "http://mail-archives.apache.org/mod_mbox/community-dev/"+x+".mbox"))

val partsFilesRDD = partsStringsRDD.map(x => (x, "/tmp/community/"+x+".mbox"))

val partsPairsKeyRDD = partsUrlsRDD.join(partsFilesRDD)

val downloadPairsRDD = partsPairsKeyRDD.map(x => x._2)

val downloadResult = downloadPairsRDD.map(x => downloadFile(x._1, x._2))