#import <Foundation/Foundation.h>
#import <Stripe/Stripe.h>

@interface MyAPIClient : NSObject <STPCustomerEphemeralKeyProvider>

- (instancetype)initWithKeyJson:(NSDictionary *)keyJson;

@property(nonatomic, retain) NSDictionary *keyJson;

@end
