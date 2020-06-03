#import <Stripe/Stripe.h>

#import "MyApiClient.h"

@implementation MyAPIClient

- (instancetype)initWithKeyJson:(NSDictionary *)keyJson {
  self = [super init];
  if (self) {
      _keyJson = keyJson;
  }
  return self;
}

- (void)createCustomerKeyWithAPIVersion:(NSString *)apiVersion completion:(STPJSONResponseCompletionBlock)completion {
    completion(_keyJson, nil);
}

@end
