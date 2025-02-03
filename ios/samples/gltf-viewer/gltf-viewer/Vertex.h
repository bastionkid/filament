//
//  Vertex.h
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import <Foundation/Foundation.h>

/**
 * Holds position of a point in 3D space
 */
@interface Vertex : NSObject

@property (nonatomic, assign) float x;
@property (nonatomic, assign) float y;
@property (nonatomic, assign) float z;

- (instancetype)initWithX:(float)x y:(float)y z:(float)z;

@end
