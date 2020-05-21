/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.qihoo360.replugin.gradle.plugin.manifest

import com.sun.istack.Nullable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider

import java.util.regex.Pattern

/**
 * @author RePlugin Team
 */
public class ManifestAPI {

    def IManifest sManifestAPIImpl

    def getActivities(Project project, String variantDir) {
        if (sManifestAPIImpl == null) {
            sManifestAPIImpl = new ManifestReader(manifestPath(project, variantDir))
        }
        sManifestAPIImpl.activities
    }

    /**
     * 获取 AndroidManifest.xml 路径
     */
    def static manifestPath(Project project, String variantDir) {
        // Compatible with path separators for window and Linux, and fit split param based on 'Pattern.quote'
        def variantDirArray = variantDir.split(Pattern.quote(File.separator))
        String variantName = ""
        variantDirArray.each {
            //首字母大写进行拼接
            variantName += it.capitalize()
        }
        println ">>> variantName:${variantName}"

        //获取processManifestTask
        def processManifestTask = project.tasks.getByName("process${variantName}Manifest")

        //如果processManifestTask存在的话
        //transform的task目前能保证在processManifestTask之后执行
        if (processManifestTask) {
            File result = null
            //正常的manifest
            File manifestOutputFile = getAndroidManifestFile(compatInvoke(processManifestTask, [
                    "getManifestOutputFile",
                    //> com.android.tools.build:gradle 3
                    "getManifestOutputDirectory"
            ]))
            //instant run的manifest
            File instantRunManifestOutputFile = getAndroidManifestFile(compatInvoke(processManifestTask, [
                    "getInstantRunManifestOutputFile",
                    //> com.android.tools.build:gradle 3
                    "getInstantRunManifestOutputDirectory",
                    //> com.android.tools.build:gradle 3.6
                    "getInstantAppManifestOutputDirectory"
            ]))

            if (manifestOutputFile == null && instantRunManifestOutputFile == null) {
                throw new GradleException("can't get manifest file")
            }

            //打印
            println " manifestOutputFile:${manifestOutputFile} ${manifestOutputFile.exists()}"
            println " instantRunManifestOutputFile:${instantRunManifestOutputFile} ${instantRunManifestOutputFile.exists()}"

            //先设置为正常的manifest
            result = manifestOutputFile

            try {
                //获取instant run 的Task
                def instantRunTask = project.tasks.getByName("transformClassesWithInstantRunFor${variantName}")
                //查找instant run是否存在且文件存在
                if (instantRunTask && instantRunManifestOutputFile.exists()) {
                    println ' Instant run is enabled and the manifest is exist.'
                    if (!manifestOutputFile.exists()) {
                        //因为这里只是为了读取activity，所以无论用哪个manifest差别不大
                        //正常情况下不建议用instant run的manifest，除非正常的manifest不存在
                        //只有当正常的manifest不存在时，才会去使用instant run产生的manifest
                        result = instantRunManifestOutputFile
                    }
                }
            } catch (ignored) {
                // transformClassesWithInstantRunForXXX may not exists
            }

            //最后检测文件是否存在，打印
            if (!result.exists()) {
                println ' AndroidManifest.xml not exist'
            }
            //输出路径
            println " AndroidManifest.xml 路径：$result"

            return result.absolutePath
        }

        return ""
    }

    /**
     * 尝试适配调用函数，方法列表中逐一尝试，调用成功则返回调用结果
     * @param invoker 调用方对象
     * @param methods 方法名列表
     * @return 调用结果
     */
    @Nullable
    private static Object compatInvoke(Object invoker, List<String> methods) {
        return methods.collect {
            try {
                invoker."${it}"()
            } catch (Exception ignored) {
                null
            }
        }.find { it != null }
    }

    /**
     * 获取AndroidManifest文件
     * @param obj 文件/目录
     * @return File
     */
    @Nullable
    private static File getAndroidManifestFile(Object obj) {
        if (obj == null) {
            return null
        }
        File dirFile
        if (obj in File) {
            if (obj.isFile()) {
                return obj
            }
            dirFile = obj
        } else if (obj in Provider) {
            //com.android.tools.build:gradle 3.4.1返回目录变更为Provider<Directory>类型
            dirFile = obj.getOrNull().asFile
        } else if (obj in DirectoryProperty) {
            //com.android.tools.build:gradle 3.6.3返回目录变更为DirectoryProperty类型
            dirFile = obj.asFile.getOrNull()
        } else {
            return null
        }
        return new File(dirFile, "AndroidManifest.xml")
    }
}
