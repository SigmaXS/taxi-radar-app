#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^TryBlock)(void);

@interface NSExceptionCatcher : NSObject

+ (BOOL)catchException:(TryBlock)tryBlock error:(NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
