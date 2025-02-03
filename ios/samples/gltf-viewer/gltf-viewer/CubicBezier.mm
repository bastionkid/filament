//
//  CubicBezier.mm
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import "CubicBezier.h"
#include <UIKit/UIKit.h>

@implementation CubicBezier

+ (NSArray<NSValue *> *)cubicBezierWithStart:(CGPoint)start
                                    control1:(CGPoint)control1
                                    control2:(CGPoint)control2
                                         end:(CGPoint)end
                                   numPoints:(int)numPoints {
    NSMutableArray<NSValue *> *points = [NSMutableArray array];
    
    for (int i = 0; i < numPoints; i++) {
        float t = (float)i / (numPoints - 1); // t is a parameter between 0 and 1
        
        // Calculate x coordinate
        float x = (1 - t) * (1 - t) * (1 - t) * start.x +
        3 * (1 - t) * (1 - t) * t * control1.x +
        3 * (1 - t) * t * t * control2.x +
        t * t * t * end.x;
        
        // Calculate y coordinate
        float y = (1 - t) * (1 - t) * (1 - t) * start.y +
        3 * (1 - t) * (1 - t) * t * control1.y +
        3 * (1 - t) * t * t * control2.y +
        t * t * t * end.y;
        
        // Wrap the point in an NSValue and add it to the array
        [points addObject:[NSValue valueWithCGPoint:CGPointMake(x, y)]];
    }
    
    return [points copy]; // Return an immutable copy of the array
}

@end
