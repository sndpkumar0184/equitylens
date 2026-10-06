FROM apache/spark:4.0.3-python3
USER root
RUN python3 -m pip install --no-cache-dir psycopg[binary]==3.2.10 kafka-python==2.2.15
RUN mkdir -p /var/lib/equitylens/checkpoints /opt/spark/work-dir/.ivy2 && chown -R 185:185 /var/lib/equitylens /opt/spark/work-dir
USER 185
WORKDIR /opt/equitylens
COPY analytics.py job.py replay.py verify_pipeline.py ./
COPY fixtures ./fixtures
COPY schemas ./schemas
ENTRYPOINT ["/opt/spark/bin/spark-submit"]
CMD ["--master", "local[2]", "--conf", "spark.sql.shuffle.partitions=4", "--conf", "spark.jars.ivy=/opt/spark/work-dir/.ivy2", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.13:4.0.3", "/opt/equitylens/job.py"]
