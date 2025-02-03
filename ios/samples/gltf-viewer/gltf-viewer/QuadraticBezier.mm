//
//  QuadraticBezier.mm
//  gltf-viewer
//
//  Created by Mercer on 03/02/25.
//

#import "QuadraticBezier.h"
#include <UIKit/UIKit.h>

@implementation QuadraticBezier

+ (NSArray<NSValue *> *)quadraticBezierWithStart:(CGPoint)start
                                         control:(CGPoint)control
                                             end:(CGPoint)end
                                     numOfPoints:(int)numOfPoints {
    NSMutableArray<NSValue *> *points = [NSMutableArray array];
    
    for (int i = 0; i < numOfPoints; i++) {
        float t = (float)i / (numOfPoints - 1); // t is a parameter between 0 and 1
        float x = (1 - t) * (1 - t) * start.x + 2 * (1 - t) * t * control.x + t * t * end.x;
        float y = (1 - t) * (1 - t) * start.y + 2 * (1 - t) * t * control.y + t * t * end.y;
        [points addObject:[NSValue valueWithCGPoint:CGPointMake(x, y)]];
    }
    
    return [points copy]; // Return an immutable copy of the array
}

@end
