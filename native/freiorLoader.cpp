#include "frei0r/include/frei0r.h"
#include "global.h"

#include <iostream>
#include <map>
#include <cstdint>

#ifdef __linux__ 
#include <dlfcn.h>
#elif _WIN32 || __CYGWIN__
#include <windows.h>
#endif

#ifdef DEBUG
    #include "imageLoader.h"
    #include <sstream>
#endif

typedef int (*f0r_init_fn)();
typedef void (*f0r_deinit_fn)();
typedef void (*f0r_get_plugin_info_fn)(f0r_plugin_info_t* info);
typedef void (*f0r_get_param_info_fn)(f0r_param_info_t* info, int param_index);
typedef void (*f0r_set_param_value_fn)(f0r_instance_t instance, f0r_param_t param, int param_index);
typedef void (*f0r_get_param_value_fn)(f0r_instance_t instance, f0r_param_t param, int param_index);
typedef void (*f0r_update_fn)(f0r_instance_t instance, double time, const uint32_t* inframe, uint32_t* outframe);
typedef void (*f0r_update2_fn)(f0r_instance_t instance, double time, const uint32_t* inframe1,const uint32_t* inframe2,const uint32_t* inframe3,uint32_t* outframe);
typedef f0r_instance_t (*f0r_construct_fn)(unsigned int width, unsigned int height);
typedef void (*f0r_destruct_fn)(f0r_instance_t instance);

struct FreiorParameter {
    const char* name;
    int type;
    const char* description;
};

struct FreiorLoadPluginRequest {
    const char* file;

    // output
    char* name;
    char* description;
    int colorModel;
    int pluginType;

    int numberOfParameters;
    FreiorParameter* parameters;
};

struct Frei0rLibraryDescriptor {
    void* handle;
    int pluginType;
    int numberOfParameters;
    FreiorParameter* parameters;
    std::map<std::pair<int, int>, f0r_instance_t> instances;

    f0r_init_fn f0r_init;
    f0r_deinit_fn f0r_deinit;
    f0r_get_plugin_info_fn f0r_get_plugin_info;
    f0r_get_param_info_fn f0r_get_param_info;
    f0r_set_param_value_fn f0r_set_param_value;
    f0r_get_param_value_fn f0r_get_param_value;
    f0r_update_fn f0r_update;
    f0r_update2_fn f0r_update2;
    f0r_construct_fn f0r_construct;
    f0r_destruct_fn f0r_destruct;
};

struct FreiorParameterRequest {
    double x;
    double y;

    float r;
    float g;
    float b;

    char* string;
};

struct RenderRequest {
    int pluginIndex;

    int width;
    int height;
    char* input;
    char* output;

    FreiorParameterRequest* parameters;

    double time;

    char* transitionToInput;
};

int globalPluginIndex = 0;
std::map<int, Frei0rLibraryDescriptor*> loadedPlugins;

void* getFunction(void* handle, const char* functionName) {
    void* result = NULL;
    #ifdef __linux__
        result = dlsym(handle, functionName);
        if (!result) {
            LOG_ERROR("Unable to find " << functionName << " function" << dlerror());
            return NULL;
        }
    #elif _WIN32 || __CYGWIN__
        result = (intFptr) GetProcAddress((HINSTANCE)handle, functionName);
        if (!result) {
            LOG_ERROR("Unable to find " << functionName << " function");
            return NULL;
        }
    #endif
    return result;
}

extern "C" {

    EXPORTED int loadPlugin(FreiorLoadPluginRequest* loadRequest) {
        void *handle;
        
        const char* file = loadRequest->file; 

        LOG("Loading plugin " << file);

        #ifdef __linux__
            handle = dlopen(file, RTLD_LAZY);
            if (!handle) {
                LOG_ERROR("Unable to load native library" << dlerror());
                return -1;
            }
        #elif _WIN32 || __CYGWIN__
            handle = LoadLibraryA(file); 
            if (!handle) {
                LOG_ERROR("Unable to load native library" << GetLastError());
                return -1;
            }
        #endif

        Frei0rLibraryDescriptor* descriptor = new Frei0rLibraryDescriptor();
        descriptor->handle = handle;

        descriptor->f0r_init = (f0r_init_fn)getFunction(handle, "f0r_init");
        descriptor->f0r_deinit = (f0r_deinit_fn)getFunction(handle, "f0r_deinit");
        descriptor->f0r_get_plugin_info = (f0r_get_plugin_info_fn)getFunction(handle, "f0r_get_plugin_info");
        descriptor->f0r_get_param_info = (f0r_get_param_info_fn)getFunction(handle, "f0r_get_param_info");
        descriptor->f0r_set_param_value = (f0r_set_param_value_fn)getFunction(handle, "f0r_set_param_value");
        descriptor->f0r_get_param_value = (f0r_get_param_value_fn)getFunction(handle, "f0r_get_param_value");
        descriptor->f0r_update = (f0r_update_fn)getFunction(handle, "f0r_update");
        descriptor->f0r_update2 = (f0r_update2_fn)getFunction(handle, "f0r_update2");
        descriptor->f0r_construct = (f0r_construct_fn)getFunction(handle, "f0r_construct");
        descriptor->f0r_destruct = (f0r_destruct_fn)getFunction(handle, "f0r_destruct");
        
        if (!descriptor->f0r_init || 
            !descriptor->f0r_deinit ||
            !descriptor->f0r_get_plugin_info ||
            !descriptor->f0r_get_param_info ||
            !descriptor->f0r_set_param_value ||
            !descriptor->f0r_get_param_value ||
            !descriptor->f0r_update ||
            !descriptor->f0r_construct ||
            !descriptor->f0r_destruct) {
                // deinitialize
                LOG_ERROR("Mandatory function definition missing");
                return -1;
        }

        descriptor->f0r_init();

        f0r_plugin_info_t pluginInfo;

        descriptor->f0r_get_plugin_info(&pluginInfo);
        LOG(pluginInfo.name << " " << pluginInfo.plugin_type << " " << pluginInfo.explanation);

        loadRequest->name = (char*)pluginInfo.name;
        loadRequest->description = (char*)pluginInfo.explanation;
        loadRequest->numberOfParameters = pluginInfo.num_params;
        loadRequest->colorModel = pluginInfo.color_model;
        loadRequest->pluginType = pluginInfo.plugin_type;

        descriptor->pluginType = pluginInfo.plugin_type;


        if (pluginInfo.num_params > 0) {
            loadRequest->parameters = new FreiorParameter[pluginInfo.num_params];
            for (int i = 0; i < pluginInfo.num_params; ++i) {
                f0r_param_info_t paramInfo;
                descriptor->f0r_get_param_info(&paramInfo, i);

                loadRequest->parameters[i].name = paramInfo.name;
                loadRequest->parameters[i].description = paramInfo.explanation;
                loadRequest->parameters[i].type = paramInfo.type;
            }
            descriptor->numberOfParameters = pluginInfo.num_params;
            descriptor->parameters = loadRequest->parameters;
        } else {
            descriptor->numberOfParameters = 0;
            descriptor->parameters = NULL;
        }

        int pluginIndex = globalPluginIndex++;
        loadedPlugins[pluginIndex] = descriptor;


        return pluginIndex;
    }

    EXPORTED void render(RenderRequest* request) {
        LOG("Rendering " << request->width << " " << request->height << " " << request->time);
        Frei0rLibraryDescriptor* descriptor = loadedPlugins[request->pluginIndex];

        auto sizePair = std::pair<int, int>(request->width, request->height);
        auto instanceIterator = descriptor->instances.find(sizePair);

        f0r_instance_t instance;
        if (instanceIterator == descriptor->instances.end()) {
            instance = descriptor->f0r_construct(request->width, request->height);
            descriptor->instances[sizePair] = instance;
        } else {
            instance = instanceIterator->second;
        }

        for (int i = 0; i < descriptor->numberOfParameters; ++i) {
            if (descriptor->parameters[i].type == F0R_PARAM_BOOL) {
                descriptor->f0r_set_param_value(instance, &request->parameters[i].x, i);
            } else if (descriptor->parameters[i].type == F0R_PARAM_COLOR) {
                descriptor->f0r_set_param_value(instance, &request->parameters[i].r, i);
            } else if (descriptor->parameters[i].type == F0R_PARAM_POSITION) {
                descriptor->f0r_set_param_value(instance, &request->parameters[i].x, i);
            } else if (descriptor->parameters[i].type == F0R_PARAM_DOUBLE) {
                descriptor->f0r_set_param_value(instance, &request->parameters[i].x, i);
            } else if (descriptor->parameters[i].type == F0R_PARAM_STRING) {
                descriptor->f0r_set_param_value(instance, request->parameters[i].string, i);
            } else {
                LOG_ERROR("UKNOWN parameter type " << descriptor->parameters[i].type);
            }
        }

        if (descriptor->pluginType == F0R_PLUGIN_TYPE_FILTER) {
            descriptor->f0r_update(instance, request->time, (uint32_t*)request->input, (uint32_t*)request->output);
        } else if (descriptor->pluginType == F0R_PLUGIN_TYPE_SOURCE) {
            descriptor->f0r_update(instance, request->time, (uint32_t*)request->input, (uint32_t*)request->output);
        } else if (descriptor->pluginType == F0R_PLUGIN_TYPE_MIXER2 && descriptor->f0r_update2 != NULL) {
            descriptor->f0r_update2(instance, request->time, (uint32_t*)request->input, (uint32_t*)request->transitionToInput, NULL, (uint32_t*)request->output);
        } else {
            LOG_ERROR("MIXER3 not supported");
        }
        LOG("Rendering finished " << request->width << " " << request->height << " " << request->time);
    }
}

#ifdef DEBUG
    int main() {
        const char* plugin = "/home/black/workspace4/tactview-frei0r-plugin/native/frei0r/src/generator/partik0l/partik0l.so";

        FreiorLoadPluginRequest request;
        request.file = plugin;

        int pluginIndex = loadPlugin(&request);

        if (pluginIndex < 0) {
            return pluginIndex;
        }

        Image* image = loadImage("/home/black/Downloads/image.ppm");

        //char* output = new char[image->width * image->height * 4];
            int w = 320;
            int h = 192;
        char* output = new char[w * h * 4];

        for (int i = 0; i < 10; ++i) {
            RenderRequest renderRequest;
            renderRequest.width = w;//image->width;
            renderRequest.height = h;//image->height;
            renderRequest.time = i / 30.0;
            //renderRequest.input = image->data;
            renderRequest.output = output;
            renderRequest.pluginIndex = pluginIndex;

            renderRequest.parameters = new FreiorParameterRequest[request.numberOfParameters];

            for (int i = 0; i < request.numberOfParameters; ++i) {
                renderRequest.parameters[i].x = 0.0;
                renderRequest.parameters[i].y = 0;

                renderRequest.parameters[i].r = 0;
                renderRequest.parameters[i].g = 0;
                renderRequest.parameters[i].b = 0;

                renderRequest.parameters[i].string = "";
            }

            render(&renderRequest);

            Image result(renderRequest.width, renderRequest.height, renderRequest.output);

            std::stringstream ss;
            ss << "/tmp/result" << i << ".ppm";

            writeImage(ss.str().c_str(), &result);
        }
    }
#endif