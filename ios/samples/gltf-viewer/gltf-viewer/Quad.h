//
//  Quad.h
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import <Foundation/Foundation.h>
#import "Vertex.h"

/**
 * Holds vertices representing each corner of a quad
 */
@interface Quad : NSObject

@property (nonatomic, assign) Vertex* topLeftVertex;
@property (nonatomic, assign) Vertex* bottomLeftVertex;
@property (nonatomic, assign) Vertex* topRightVertex;
@property (nonatomic, assign) Vertex* bottomRightVertex;

- (instancetype)initWithTopLeftVertex:(Vertex*)topLeftVertex
                     bottomLeftVertex:(Vertex*)bottomLeftVertex
                       topRightVertex:(Vertex*)topRightVertex
                    bottomRightVertex:(Vertex*)bottomRightVertex;

@end
