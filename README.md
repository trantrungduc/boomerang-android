# boomerang-android
Create video boomerang effect.

Origin Video:
[![IMAGE ALT TEXT](http://img.youtube.com/vi/Blt72CyZadE/0.jpg)](https://www.youtube.com/watch?v=LnwAoWuJP7o "Origin Video")

Boomerang Video:
[![IMAGE ALT TEXT](http://img.youtube.com/vi/LnwAoWuJP7o/0.jpg)](https://www.youtube.com/watch?v=LnwAoWuJP7o "Boomerang Video")

Using FFMPEG:
```
FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(mVideoOutputFile);
List<Frame> loop = new ArrayList<Frame>();

grabber.start();
File boomFile = new File(makeUriString("mp4"));
Log.i("MainActivity","File: "+boomFile.getAbsolutePath());
FFmpegFrameRecorder frecorder = new FFmpegFrameRecorder(boomFile, grabber.getImageWidth(), grabber.getImageHeight());
frecorder.setFrameRate(grabber.getFrameRate());
frecorder.setSampleRate(grabber.getSampleRate());
frecorder.setFormat(grabber.getFormat());
frecorder.setVideoCodec(grabber.getVideoCodec());
frecorder.setAudioChannels(grabber.getAudioChannels());
frecorder.setVideoOption("preset", "ultrafast");
frecorder.setVideoQuality(18);

frecorder.start();

Frame frame;
double max = Math.round(2 * grabber.getFrameRate());
while ((frame = grabber.grabImage()) != null) {
	int current = grabber.getFrameNumber();
	if (current >= max) {
		break;
	} else {
		if (current % 2 == 0) {
			loop.add(frame.clone());
		}
	}

}
frame = null;
grabber.stop();
grabber.release();
for (int k = 0; k < 3; k++) {
	for (Frame frame1 : loop) {
		frecorder.record(frame1);
	}
	for (int i=loop.size()-1;i>=0;i--){
		frecorder.record(loop.get(i));
	}
}
loop.clear();loop=null;
frecorder.stop();
frecorder.release();
```
