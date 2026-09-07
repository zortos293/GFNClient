#version 440
#extension GL_GOOGLE_include_directive : enable
#include "hdrcolor.glsl"
layout(location = 0) in vec2 itemPosition;
layout(location = 0) out vec4 fragColor;
layout(std140, binding = 0) uniform Composition {
    mat4 matrix;
    vec4 bounds;
    vec4 videoRect;
    vec4 parameters;
    vec4 colorParameters;
};
layout(binding = 1) uniform sampler2D videoTexture;
void main()
{
    vec2 uv = (itemPosition - videoRect.xy) / max(videoRect.zw, vec2(1.0));
    vec3 color = vec3(0.0);
    if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0))))
        color = videoColor(texture(videoTexture, uv).rgb, colorParameters.x,
                           colorParameters.y, colorParameters.z, colorParameters.w);
    fragColor = vec4(color * parameters.x, parameters.x);
}
