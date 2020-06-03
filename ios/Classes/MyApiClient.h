@interface MyAPIClient : NSObject <STPCustomerEphemeralKeyProvider>

- (instancetype)initWithKeyJson:(NSDictionary *)keyJson;

@property(nonatomic, retain) NSDictionary *keyJson;

@end
