package pl.vachacz;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.log4j.Logger;
import pl.vachacz.transcoder.XmlProcessor;
import pl.vachacz.transcoder.XmlTranscoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TranscodeLambda implements RequestHandler<SQSEvent, String> {

	private static final Logger log = Logger.getLogger(TranscodeLambda.class);

	@Override
	public String handleRequest(SQSEvent sqsEvent, Context context) {
		log.info("Transcoding lambda started");

		ExecutorService executorService = Executors.newFixedThreadPool(10);

		sqsEvent.getRecords().forEach(message -> {
			executorService.submit(() -> {
				handleS3Event(S3Event.parseJson(message.getBody()));
			});
		});

		log.info("Tasks submitted");
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(300, TimeUnit.SECONDS)) {
				log.info("Termination timeout occurred");
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			log.info("Exception during lambda termination", e);
			executorService.shutdownNow();
		}
		log.info("Transcoding completed");
		return "done";
	}

	private void handleS3Event(S3EventNotification s3Event) {

		S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

		String srcBucket = record.getS3().getBucket().getName();
		String srcKey = record.getS3().getObject().getKey();

		log.info("S3 event handler started: " + srcKey);

		AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
		S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));

		try (
				InputStream fi = s3Object.getObjectContent();
				InputStream gzi = new GzipCompressorInputStream(fi);
				XmlProcessor xmlProcessor = new XmlProcessor(gzi);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
		) {
			XmlTranscoder transcoder = new XmlTranscoder(xmlProcessor, out);
			transcoder.transcode();

			String targetBucket = System.getenv("OUTPUT_BUCKET");
			String targetKey = srcKey + ".csv";

			InputStream inStream = new ByteArrayInputStream( out.toByteArray() );

			s3Client.putObject(targetBucket, targetKey, inStream, new ObjectMetadata());
			s3Client.deleteObject(srcBucket, srcKey);
		} catch (Exception e) {
			log.error("Exception caught", e);
			throw new RuntimeException(e);
		}

		log.info("S3 event handler completed: " + srcKey);
	}

}
