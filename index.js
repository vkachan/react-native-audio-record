import { NativeModules, NativeEventEmitter } from 'react-native';
const { RNAudioRecord } = NativeModules;
const EventEmitter = new NativeEventEmitter(RNAudioRecord);

const AudioRecord = {};

AudioRecord.initRecorder = options => RNAudioRecord.initRecorder(options);
AudioRecord.start = () => RNAudioRecord.start();
AudioRecord.stop = () => RNAudioRecord.stop();
AudioRecord.getMaxAmplitude = () => RNAudioRecord.getMaxAmplitude();
AudioRecord.initPlayer = options => RNAudioRecord.initPlayer(options);
AudioRecord.pauseMediaPlayer = () => RNAudioRecord.pauseMediaPlayer();
AudioRecord.playMediaPlayer = () => RNAudioRecord.playMediaPlayer();
AudioRecord.stopMediaPlayer = () => RNAudioRecord.stopMediaPlayer();
AudioRecord.getTimeMediaPlayer = () => RNAudioRecord.getTimeMediaPlayer();
AudioRecord.getDuration = () => RNAudioRecord.getDuration();

const eventsMap = {
  data: 'data'
};

AudioRecord.on = (event, callback) => {
  const nativeEvent = eventsMap[event];
  if (!nativeEvent) {
    throw new Error('Invalid event');
  }
  EventEmitter.removeAllListeners(nativeEvent);
  return EventEmitter.addListener(nativeEvent, callback);
};

export default AudioRecord;
