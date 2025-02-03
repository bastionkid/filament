//
//  CylinderUtils.h
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import <Foundation/Foundation.h>
#import "Vertex.h"
#import "Quad.h"

@interface CylinderUtils : NSObject

+ (NSArray<Vertex *> *)getPointsAlongCircumferenceWithCenterX:(float)centerX
                                                      centerY:(float)centerY
                                                      centerZ:(float)centerZ
                                                       radius:(float)radius
                                                  numOfPoints:(int)numOfPoints;

+ (NSArray<Quad *> *)getQuadVerticesWithPoint1Vertices:(NSArray<Vertex *> *)point1Vertices
                                        point2Vertices:(NSArray<Vertex *> *)point2Vertices;

@end
