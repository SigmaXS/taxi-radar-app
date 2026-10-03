#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface NSExceptionCatcher : NSObject

+ (BOOL)catchException:(void(NS_NOESCAPE ^)(void))tryBlock error:(__autoreleasing NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
