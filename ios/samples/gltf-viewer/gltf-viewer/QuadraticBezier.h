//
//  QuadraticBezier.h
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

@interface QuadraticBezier : NSObject

/**
 * A quadratic Bézier curve is defined by 3 points: a start point, a control point,
 * and an end point. The curve smoothly transitions from start to end, influenced by control point.
 */
+ (NSArray<NSValue *> *)quadraticBezierWithStart:(CGPoint)start
                                         control:(CGPoint)control
                                             end:(CGPoint)end
                                     numOfPoints:(int)numOfPoints;

@end
