//
//  Vertex.mm
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import "Vertex.h"

@implementation Vertex

- (instancetype)initWithX:(float)x y:(float)y z:(float)z {
    self = [super init];
    if (self) {
        _x = x;
        _y = y;
        _z = z;
    }
    return self;
}

@end
