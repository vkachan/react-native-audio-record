#import "RNAudioRecord.h"

@implementation RNAudioRecord

NSInteger *cAmplitude;

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(init:(NSDictionary *) options) {
    RCTLogInfo(@"init");
    _recordState.mDataFormat.mSampleRate        = 44100;
    _recordState.mDataFormat.mBitsPerChannel    = 16;
    _recordState.mDataFormat.mChannelsPerFrame  = 2;
    _recordState.mDataFormat.mBytesPerPacket    = (_recordState.mDataFormat.mBitsPerChannel / 8) * _recordState.mDataFormat.mChannelsPerFrame;
    _recordState.mDataFormat.mBytesPerFrame     = _recordState.mDataFormat.mBytesPerPacket;
    _recordState.mDataFormat.mFramesPerPacket   = 1;
    _recordState.mDataFormat.mReserved          = 0;
    _recordState.mDataFormat.mFormatID          = kAudioFormatLinearPCM;
    _recordState.mDataFormat.mFormatFlags       = _recordState.mDataFormat.mBitsPerChannel == 8 ? kLinearPCMFormatFlagIsPacked : (kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked);


    _recordState.bufferByteSize = 2048;
    _recordState.mSelf = self;

    NSString *fileName = options[@"wavFile"] == nil ? @"audio.wav" : options[@"wavFile"];
    NSString *wavFileDir = options[@"wavFileDir"];
    _filePath = [NSString stringWithFormat:@"%@/%@", wavFileDir, fileName];
}

RCT_EXPORT_METHOD(start) {
    RCTLogInfo(@"start");

    // most audio players set session category to "Playback", record won't work in this mode
    // therefore set session category to "Record" before recording
    [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error:nil];

    _recordState.mIsRunning = true;
    _recordState.mCurrentPacket = 0;

    CFURLRef url = CFURLCreateWithString(kCFAllocatorDefault, (CFStringRef)_filePath, NULL);
    AudioFileCreateWithURL(url, kAudioFileWAVEType, &_recordState.mDataFormat, kAudioFileFlags_EraseFile, &_recordState.mAudioFile);
    CFRelease(url);

    AudioQueueNewInput(&_recordState.mDataFormat, HandleInputBuffer, &_recordState, NULL, NULL, 0, &_recordState.mQueue);
    for (int i = 0; i < kNumberBuffers; i++) {
        AudioQueueAllocateBuffer(_recordState.mQueue, _recordState.bufferByteSize, &_recordState.mBuffers[i]);
        AudioQueueEnqueueBuffer(_recordState.mQueue, _recordState.mBuffers[i], 0, NULL);
    }
    AudioQueueStart(_recordState.mQueue, NULL);
}

RCT_REMAP_METHOD(stop,
                 stopWithResolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(__unused RCTPromiseRejectBlock)reject) {
        RCTLogInfo(@"stop");
        if (_recordState.mIsRunning) {
            _recordState.mIsRunning = false;
            AudioQueueStop(_recordState.mQueue, true);
            AudioQueueDispose(_recordState.mQueue, true);
            AudioFileClose(_recordState.mAudioFile);
        }
        resolve(_filePath);
        unsigned long long fileSize = [[[NSFileManager defaultManager] attributesOfItemAtPath:_filePath error:nil] fileSize];
        RCTLogInfo(@"file path %@", _filePath);
        RCTLogInfo(@"file size %llu", fileSize);
}

RCT_EXPORT_METHOD(pause) {
        RCTLogInfo(@"pause");
        if (_recordState.mIsRunning) {
            _recordState.mIsRunning = false;
            AudioQueueStop(_recordState.mQueue, true);
        }
}

RCT_EXPORT_METHOD(resume) {
    RCTLogInfo(@"resume");
    _recordState.mIsRunning = true;
    for (int i = 0; i < kNumberBuffers; i++) {
        AudioQueueAllocateBuffer(_recordState.mQueue, _recordState.bufferByteSize, &_recordState.mBuffers[i]);
        AudioQueueEnqueueBuffer(_recordState.mQueue, _recordState.mBuffers[i], 0, NULL);
    }
    AudioQueueStart(_recordState.mQueue, NULL);
}

RCT_REMAP_METHOD(getMaxAmplitude,
                 getMaxAmplitudeWithResolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve([NSNumber numberWithLong:labs(cAmplitude)]);
}

void HandleInputBuffer(void *inUserData,
                       AudioQueueRef inAQ,
                       AudioQueueBufferRef inBuffer,
                       const AudioTimeStamp *inStartTime,
                       UInt32 inNumPackets,
                       const AudioStreamPacketDescription *inPacketDesc) {
    AQRecordState* pRecordState = (AQRecordState *)inUserData;

    if (!pRecordState->mIsRunning) {
        return;
    }

    if (AudioFileWritePackets(pRecordState->mAudioFile,
                              false,
                              inBuffer->mAudioDataByteSize,
                              inPacketDesc,
                              pRecordState->mCurrentPacket,
                              &inNumPackets,
                              inBuffer->mAudioData
                              ) == noErr) {
        pRecordState->mCurrentPacket += inNumPackets;
    }

    short *samples = (short *) inBuffer->mAudioData;
    UInt32 sampleCount = (inBuffer->mAudioDataBytesCapacity / sizeof (SInt16));
    for (UInt32 i = 0; i < sampleCount; i++) {
        cAmplitude = samples[i];
    }
    AudioQueueEnqueueBuffer(pRecordState->mQueue, inBuffer, 0, NULL);
}

union intToFloat
{
    uint32_t i;
    float fp;
};

- (NSArray<NSString *> *)supportedEvents {
    return @[@"data"];
}

- (void)dealloc {
    RCTLogInfo(@"dealloc");
    AudioQueueDispose(_recordState.mQueue, true);
}

@end
