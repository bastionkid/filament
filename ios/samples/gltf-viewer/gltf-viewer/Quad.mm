//
//  Quad.mm
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import "Quad.h"

@implementation Quad

- (instancetype)initWithTopLeftVertex:(Vertex*)topLeftVertex
                     bottomLeftVertex:(Vertex*)bottomLeftVertex
                       topRightVertex:(Vertex*)topRightVertex
                    bottomRightVertex:(Vertex*)bottomRightVertex {
    self = [super init];
    if (self) {
        _topLeftVertex = topLeftVertex;
        _bottomLeftVertex = bottomLeftVertex;
        _topRightVertex = topRightVertex;
        _bottomRightVertex = bottomRightVertex;
    }
    return self;
}

@end
