//
//  CylinderUtils.mm
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import "CylinderUtils.h"
#import <math.h>

@implementation CylinderUtils

+ (NSArray<Vertex *> *)getPointsAlongCircumferenceWithCenterX:(float)centerX
                                                      centerY:(float)centerY
                                                      centerZ:(float)centerZ
                                                       radius:(float)radius
                                                  numOfPoints:(int)numOfPoints {
    NSMutableArray<Vertex *> *vertices = [NSMutableArray array];
    float angleIncrement = (2 * M_PI) / numOfPoints;
    
    for (int i = 0; i < numOfPoints; i++) {
        float theta = i * angleIncrement;
        float x = centerX + (radius * cosf(theta));
        float y = centerY + (radius * sinf(theta));
        float z = centerZ;
        
        Vertex *vertex = [[Vertex alloc] initWithX:x y:y z:z];
        [vertices addObject:vertex];
    }
    
    return [vertices copy];
}

+ (NSArray<Quad *> *)getQuadVerticesWithPoint1Vertices:(NSArray<Vertex *> *)point1Vertices
                                        point2Vertices:(NSArray<Vertex *> *)point2Vertices {
    // Assert that the sizes of point1Vertices and point2Vertices are the same and even
    NSAssert(point1Vertices.count == point2Vertices.count && point1Vertices.count % 2 == 0,
             @"Both vertex lists must have the same size and be even.");
    
    NSMutableArray<Quad *> *quads = [NSMutableArray array];
    
    // Add the first element to the end of each list to connect the cylinder ends
    NSMutableArray<Vertex *> *extendedPoint1Vertices = [point1Vertices mutableCopy];
    [extendedPoint1Vertices addObject:point1Vertices.firstObject];
    
    NSMutableArray<Vertex *> *extendedPoint2Vertices = [point2Vertices mutableCopy];
    [extendedPoint2Vertices addObject:point2Vertices.firstObject];
    
    // Zip the extended lists and create quads
    for (int i = 0; i < extendedPoint1Vertices.count - 1; i++) {
        Vertex *topLeftVertex = extendedPoint1Vertices[i];
        Vertex *bottomLeftVertex = extendedPoint2Vertices[i];
        Vertex *topRightVertex = extendedPoint1Vertices[i + 1];
        Vertex *bottomRightVertex = extendedPoint2Vertices[i + 1];
        
        Quad *quad = [[Quad alloc] initWithTopLeftVertex:topLeftVertex
                                        bottomLeftVertex:bottomLeftVertex
                                          topRightVertex:topRightVertex
                                       bottomRightVertex:bottomRightVertex];
        [quads addObject:quad];
    }
    
    return [quads copy];
}

@end
