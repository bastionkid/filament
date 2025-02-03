//
//  CubicBezier.h
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import <Foundation/Foundation.h>

@interface CubicBezier : NSObject

+ (NSArray<NSValue *> *)cubicBezierWithStart:(CGPoint)start
                                    control1:(CGPoint)control1
                                    control2:(CGPoint)control2
                                         end:(CGPoint)end
                                   numPoints:(int)numPoints;

@end
