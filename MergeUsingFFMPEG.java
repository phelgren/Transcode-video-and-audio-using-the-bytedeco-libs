/**
 * 
 */
package org.bsfinternational.media.server;

import static org.bytedeco.javacpp.avutil.AV_SAMPLE_FMT_S16;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;




import org.apache.log4j.Logger;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.bsfinternational.media.server.MediaServerUtils;


/**
 * @author Pete
 *
 * @note The real challenge here is getting the javacv environment properly set up.  I am not sure how difficult
 * this will be on deployment.  
 */
public class MergeUsingFFMPEG implements Runnable{

	// This will have just about the same properties as the MP3FileMerge.  We need a runnable so we can have multiple
	// threads processing files, both audio and video...not sure how well this will scale....
	
	private static Logger log = Logger.getLogger(MergeUsingFFMPEG.class.getName());
	
	StringBuilder introPath;
	File intro;
	StringBuilder lecture;
	StringBuilder mergedFile;
	String classID = "98765";
	String templateFilePath = "";
	String imageURL = "";
	StringBuilder imagepath;
	String comment="";
	String uploadersEmailAddress = "";
	String emailURL = "";
	String accessCode ="";
	int seqNum = 0;
	String mediaType = "audio";
	boolean oneOfTwo; // Accommodates video with audio
	
	// just for testing 
	String input1;
	String input2;
	String output;
	boolean debug = true;
	boolean isVideo = false;
	
	public MergeUsingFFMPEG(StringBuilder introPath, StringBuilder lecture, StringBuilder mergedFile, 
			String classID, String templateFilePath, String imageURL, StringBuilder imagepath, String comment,
			String uploadersEmailAddress, String emailURL,String accessCode, int seqNum, String mediaType, boolean oneOfTwo) {
		// TODO Auto-generated constructor stub
		// 3/31/15 added the emailURL - moved ALL email notifications into this routine
		
		this.introPath = introPath;
		this.lecture = lecture;
		this.mergedFile = mergedFile;
		this.classID = classID;
		this.templateFilePath = templateFilePath; // for notification
		this.imageURL = imageURL;
		this.imagepath = imagepath;
		this.comment = comment;
		this.uploadersEmailAddress = uploadersEmailAddress;
		this.emailURL = emailURL;
		this.accessCode = accessCode;
		this.seqNum = seqNum;
		this.mediaType = mediaType;
		this.oneOfTwo = oneOfTwo;
		
		this.isVideo = mediaType.equals("video");
		
		// So this is a WEE bit different now....
		// The run method really needs just the three files AS STRINGS!!
		// The rest of the params are used to construct the input and output files...

		// Evaluate the video vs audio  copyrights are either mp3 or mp4 and output is either mp3 or mp4
		if(this.isVideo){
			this.input1 = introPath.toString() + "BSFCopyright.mp4";
			mergedFile.append(".mp4");
		}
		else
		{
			this.input1 = introPath.toString() + "BSFCopyright.mp3";
			mergedFile.append(".mp3");
		}	
		this.input2 = lecture.toString();  // TEMPLecture
		
		this.output = mergedFile.toString(); // I think we need an extension here
		
	}
	
	// The real work is here:
	
	public void run(){
		// 09/07/2017 
		// Removed the the read and merge of the "intro" file to set recording parameters
		// Problem child was the framerate 
		// So set values manually and remove references to "intro"
		
		// Just a basic test to start
		long starttime = System.currentTimeMillis();
		
		// So our *normal* first step was getting the lecture processing record from the seqNum passed in
		//  We have externalized the updates to the lecture processing record (issued a call to the api before this guys was invoked)

	
   	 if(!oneOfTwo) // we are going to skip this on a "two-fer" - video and audio
   	
   		 startRecordingMediaProcessing(seqNum);
   	 
   	 
   	 	// this will catch a class not found exception which is rare but can indicate that the JNI stuff is jacked up....
   	 
   	 	try{
   	 		
   	 		Class fg = Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
   	 	}
   	 	catch (NoClassDefFoundError cnfe){
	         try {
	        	 completeLectureProcessing(seqNum,Integer.parseInt(classID), uploadersEmailAddress, starttime, 
						 0, 0, 0, "unknown", "Java error - Frame grabber not found", seqNum, mediaType);
				
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
   	 	} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block

		}

		long intermediatetime = System.currentTimeMillis();
		 //FrameGrabber intro = new FFmpegFrameGrabber(input1); 
		 FrameGrabber lecture = new FFmpegFrameGrabber(input2);
		 boolean fileMergedSuccessfully = false;
		 SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
		 String error = "";
		 int fileSize = 0;
		 
		 // I think the values are lost when the FrameGrabbers are stopped so we need 
		 // save them in variables as we go.
		 String mediaFormat = "";
		 String videoFormat = "";


		 // We'll need these 
		 // AUDIO:		

		 int audio_audioChannels = 2;
		 int audio_audioSampleRate = 44100;
		 int audio_audioSampleFormat = 6;
		 int audio_audioBitrate = 96000;
		 int audio_audiocodec = 86017;
		 String audioFormat = "mp3";
		 
		 //VIDEO:		

		 int video_audioChannels = 2;
		 int video_audioSampleRate = 44100;
		 int video_audioSampleFormat = 8; /// or is it 1 ?
		 int videoBitrate = 147402;
		 int video_audioBitrate = 96000;
		 int videoCodec = 28;
		 int video_audioCodec = 86018;
		 double videoframerate = 29.97;
		 
		 FrameRecorder recorder = null;

	         try {
	        	         	 
				//intro.start();
				
				if(isVideo){
					
					lecture.start();

					videoFormat = lecture.getFormat();

					mediaFormat = videoFormat;
					
					double lecture_videoFrameRate = lecture.getFrameRate();
					int lecture_audioSampleFormat = lecture.getSampleFormat();
					int lecture_audioCodec = lecture.getAudioCodec();
					int lecture_videoCodec = lecture.getVideoCodec();
					video_audioSampleRate = lecture.getSampleRate();
					
		        	 recorder = new FFmpegFrameRecorder(output, 640,
                             480, video_audioChannels); 

			         //recorder.setFrameRate(videoframerate);
			         recorder.setFrameRate(lecture_videoFrameRate);
			         
			         // There is an issue with fltp format  I am going to try switching but might run into an issue in Linux
			         if(avutil.AV_SAMPLE_FMT_FLTP ==  video_audioSampleFormat && !org.apache.commons.lang3.SystemUtils.IS_OS_LINUX)
			        	 video_audioSampleFormat = avutil.AV_SAMPLE_FMT_S16;// Looks like we have to set this rather than pull it from the intro
			         recorder.setSampleFormat( video_audioSampleFormat); 
			         recorder.setSampleRate(video_audioSampleRate);
		        	 recorder.setVideoBitrate(videoBitrate);
		        	 recorder.setAudioBitrate(video_audioBitrate);
		        	 recorder.setFormat(videoFormat);
		        	 recorder.setVideoCodec(videoCodec);
		        	 recorder.setAudioCodec(video_audioCodec);
		        	

		         }
		         else{
		        
		        	lecture.start(); 
		        	 
					mediaFormat = audioFormat;

		        	 // Just audio
		        	 recorder = new FFmpegFrameRecorder(output,audio_audioChannels); 
		        	 
		        	 if(oneOfTwo)
		        		 recorder.setAudioCodec(audio_audiocodec);
			         recorder.setSampleFormat(audio_audioSampleFormat); 
			         recorder.setSampleRate(audio_audioSampleRate);
			         recorder.setAudioBitrate(audio_audioBitrate);
		         }
				 
		         recorder.start();
		         
		         if(debug)
					System.out.println("Opening output file:" + (System.currentTimeMillis()-intermediatetime));

		         Frame frame; 

				 intermediatetime = System.currentTimeMillis();
				 
				 long t = recorder.getTimestamp();
				 long lastts = 0L;
				 
		         while ((frame = lecture.grabFrame()) != null) {
		        	 if(isVideo){
		        		 
						 long lts = lecture.getTimestamp();
						 long ts = t+lts;
		        		 if(ts>recorder.getTimestamp())
		        			 recorder.setTimestamp(ts);
		        		 lastts = ts;
		        		 
		        	 }
		        	 // One more...record only when audio is being extracted from MP4 and there is an audio track to record
		        	 if(oneOfTwo){
		        		 if(frame.audioChannels>0 && frame.sampleRate>0)
		        			 recorder.record(frame); 
		        	 }
		        	 else
		        		 recorder.record(frame);
		        	 
		         } 
		         
		         if(debug)
					System.out.println("Done processing lecture:" + (System.currentTimeMillis()-intermediatetime));

		         // Handled in Finally block now....
		         //lecture.stop();
		        // recorder.stop(); 

	                
		         // get the length of the processed lecture in bytes
		         
		         File l = new File(input2);
		         if(l.exists())
		        	 fileSize = (int) (l.length() * .000001);
		         
		         fileMergedSuccessfully = true; // made it all the way through

		         if(debug)
					System.out.println("Done merging files in " + (((System.currentTimeMillis()-intermediatetime)!=0)?((System.currentTimeMillis()-intermediatetime)/1000):(System.currentTimeMillis()-intermediatetime))+ " seconds.");
		         
		         log.info("File "+input2 + " processed in " + (((System.currentTimeMillis()-starttime)!=0)?((System.currentTimeMillis()-starttime)/1000):(System.currentTimeMillis()-starttime))+ " seconds. On: " + sdf.format(new Date())) ;
		         
		         intermediatetime = System.currentTimeMillis();
		         
	         } catch (Exception e) {
					// TODO Auto-generated catch block
	        	 	error = e.getMessage()!=null?e.getMessage():"An error occurred but is not known";
	        	 	
					//e.printStackTrace();
				}
	         finally{
	        	 if(recorder!=null)
					try {
						recorder.stop();
					} catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
						
						error = e.getMessage()!=null?e.getMessage():"An error occurred but is not known";
						
						//e.printStackTrace();
					}
	        	 if(lecture!=null)
					try {
						lecture.stop();
					} catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
						error = e.getMessage()!=null?e.getMessage():"An error occurred but is not known";
						
						//e.printStackTrace();
					}
	        	 
	        	 
	         }
	 		
	         String nl = System.getProperty("line.separator");
	         StringBuilder sbSubject = new StringBuilder();
	         StringBuilder sbBody = new StringBuilder();
	         int lectureRecordingID = 9999999; // changed to use 9999999 as a placeholder for a failed process 
	         									// the next upload would show the error message 
	         if(fileMergedSuccessfully){
	        	 
		         if(debug)
						System.out.println("Completed processing on "+ mediaType + " file without error:" + (System.currentTimeMillis()-starttime));
		         
		         if(!oneOfTwo) // no notification on first pass
		        	 
	        	 // Create the lecture recording record
	        	 try{
	        		 // 01-13-2016 
	        		 // Moved the addLectureRecord BACK to the upload completion step which should have the side effect of clearing the 
	        		 // volunteer notification records (which is what we want...)
		        	 // LectureRecordingLocalServiceUtil.addLectureRecording(uploadersEmailAddress,emailURL,accessCode,comment);
		        	 // We have two emails to send although one will be a bit redundant:
		        	 // First we send a notification to the MLT uploader to let them know that the processing is complete
		        	 sbSubject.append("Lecture upload and processing for class number ").append(classID).append(" completed normally.");
		        	 // Build the email body

			         sbBody.append("Some details on the process are: ").append(nl).append("<br>");
			         if(isVideo){
			        	 sbBody.append("The file is a video file. ").append(nl).append("<br>")
			        	.append("The video codec is: ").append(videoCodec).append(nl).append("<br>");
			         }
			         else
			        	 sbBody.append("The file is an audio file. ").append(nl).append("<br>");
			         sbBody.append("File format is: ").append( mediaFormat).append(nl).append("<br>")
			         .append("The channels are: ").append(2).append(nl).append("<br>")
			         //.append("The audio codec is: ").append(introAudioCodec).append(nl).append("<br>")
			         .append("The file size is: ").append(fileSize).append("mb").append(nl).append("<br>")
			         .append("<br>")
			         .append("You can now proceed to notify your volunteers using the volunteer notification program. ").append(nl).append("<br>")
			         .append("For your information the volunteer notification code for this upload is: " ).append(accessCode);
			        
			        // Notify the uploader
			        sendNotificationEmail(uploadersEmailAddress, uploadersEmailAddress, sbSubject.toString(),sbBody.toString());
					
					// then we send the notifications to the leaders who should be receiving the lecture 
//******* DONT FORGET TO UNCOMMENT
			         MediaServerUtils.notifyByEmail(classID, templateFilePath, imageURL, imagepath, comment);
		        	 // finally we will need the lectureRecording ID for the log
		        	 lectureRecordingID =  MediaServerUtils.getLectureRecordingID(accessCode);
	        	 }
	        	 catch (Exception e){
	        		 error = e.getMessage()!=null?e.getMessage():"An error occurred but is not known";
	        		
	        	 }
	        	 
	         }
			else
				if(!oneOfTwo) // no notification on first pass
				try {

			         sbSubject.append("The file processing for class number ").append(classID).append(" did NOT complete normally.");
			         	
			         sbBody.append("Some details on the failed process are: ").append(nl).append("<br>");
			         
			         if(isVideo){
			        	 sbBody.append("The file is a video file. ").append(nl).append("<br>")
			        	.append("The video codec is: ").append(videoCodec).append(nl).append("<br>");
			         }
			         else
			        	 sbBody.append("The file is an audio file. ").append(nl).append("<br>");
			         
			         sbBody.append("File format is: ").append( mediaFormat).append(nl).append("<br>")
			         .append("The channels are: ").append(2).append(nl).append("<br>")
			         //.append("The audio codec is: ").append(introAudioCodec).append(nl).append("<br>")
			         .append("The file size is: ").append(fileSize).append("mb").append(nl).append("<br>")
			         .append("The error message is: ").append(error);
			         
			         sendNotificationEmail(uploadersEmailAddress, uploadersEmailAddress, sbSubject.toString(),sbBody.toString());
				
				} catch (Exception e) {
					// TODO Auto-generated catch block
					error = e.getMessage()!=null?e.getMessage():"An error occurred but is not known";
				}
	         
	         long endTime = System.currentTimeMillis();
	         int elapsedTime = (int) ((endTime-starttime)/1000);
	         
	         if(debug)
					System.out.println(sbBody.toString());
	         
	         // So at this point all we need to do is to log the upload and results.
	         if(!oneOfTwo || error.length()>0) // no logging on first pass but DO log on error!
	         try {
	        	 MediaServerUtils.completeLectureProcessing(seqNum,Integer.parseInt(classID), uploadersEmailAddress, starttime, 
						 endTime, elapsedTime, fileSize, mediaFormat, error, lectureRecordingID,mediaType);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

		}

	}
}
