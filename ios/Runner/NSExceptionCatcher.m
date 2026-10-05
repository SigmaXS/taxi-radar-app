#import "NSExceptionCatcher.h"

@implementation NSExceptionCatcher

+ (BOOL)catchException:(TryBlock)tryBlock error:(NSError * _Nullable * _Nullable)error {
    @try {
        tryBlock();
        return YES;
    }
    @catch (NSException *exception) {
        if (error) {
            *error = [NSError errorWithDomain:@"com.taxiradar.exception"
                                         code:1
                                     userInfo:@{
                                         NSLocalizedDescriptionKey: exception.reason ?: (exception.name ?: @"Unknown Objective-C exception"),
                                         @"NSExceptionName": exception.name ?: @"UnknownName"
                                     }];
        }
        return NO;
    }
}

@end
