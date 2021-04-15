/*
 * Copyright 2017 Comcast Cable Communications Management, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xfinity.blueprint_compiler

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.sun.tools.javac.code.Symbol.ClassSymbol
import com.sun.tools.javac.code.Symbol.MethodSymbol
import com.sun.tools.javac.code.Symbol.VarSymbol
import com.sun.tools.javac.code.Type
import com.xfinity.blueprint_annotations.ClickableComponentBinder
import com.xfinity.blueprint_annotations.ComponentViewClass
import com.xfinity.blueprint_annotations.ComponentViewHolder
import com.xfinity.blueprint_annotations.ComponentViewHolderBinder
import com.xfinity.blueprint_annotations.DefaultPresenter
import com.xfinity.blueprint_annotations.DefaultPresenterConstructor
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashSet
import java.util.Locale
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.MirroredTypeException
import javax.tools.Diagnostic

@AutoService(Processor::class)
class BlueprintProcessor : AbstractProcessor() {
    private val messager = Messager()
    @Synchronized override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        messager.init(processingEnv)
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        val annotations: MutableSet<String> = LinkedHashSet()
        annotations.add(ComponentViewClass::class.java.canonicalName)
        annotations.add(ComponentViewHolder::class.java.canonicalName)
        annotations.add(ComponentViewHolderBinder::class.java.canonicalName)
        annotations.add(DefaultPresenter::class.java.canonicalName)
        annotations.add(DefaultPresenterConstructor::class.java.canonicalName)
        return annotations
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        var viewHolderPackageName: String? = null

        lateinit var outputPackageName: String
        lateinit var appPackageName: String

        val componentViewInfoList = mutableListOf<ComponentViewInfo>()
        for (annotatedElement in roundEnv.getElementsAnnotatedWith(ComponentViewHolder::class.java)) {
            // annotation is only allowed on classes, so we can safely cast here
            val annotatedClass = annotatedElement as TypeElement
            if (!isValidClass(annotatedClass)) {
                continue
            }
            if (viewHolderPackageName == null) {
                try {
                    viewHolderPackageName = processingEnv.elementUtils.getPackageName(annotatedClass)
                } catch (e: UnnamedPackageException) {
                    e.printStackTrace()
                }

                //Epic hackery
                if (viewHolderPackageName != null) {
                    val packageNameTokens = viewHolderPackageName.split(".")
                    appPackageName = if (packageNameTokens.size >= 3) {
                        "${packageNameTokens[0]}.${packageNameTokens[1]}.${packageNameTokens[2]}"
                    } else {
                        viewHolderPackageName
                    }

                    outputPackageName = "$appPackageName.blueprint"
                }
            }

            val viewType = annotatedElement.getAnnotation(ComponentViewHolder::class.java).viewType
            val viewHolderClassName = (annotatedClass as ClassSymbol).fullname.toString()
            val children: MutableMap<String, String> = HashMap()
            val methodNames: MutableList<String> = ArrayList()
            for (enclosedElement in annotatedElement.getEnclosedElements()) {
                // We only look at fields
                if (enclosedElement is MethodSymbol) {
                    methodNames.add(enclosedElement.simpleName.toString().toLowerCase(Locale.ROOT))
                }
            }
            for (enclosedElement in annotatedElement.getEnclosedElements()) {
                // We only look at fields
                if (enclosedElement.kind.isField) {
                    val variable = enclosedElement as VariableElement
                    val variableName = enclosedElement.getSimpleName().toString()

                    //we'll only generate methods for this field if this class has
                    // a getter for the field. We'll assume a naming convention of getFieldName()
                    var hasGetter = false
                    for (methodName in methodNames) {
                        if (methodName == "get" + variableName.toLowerCase(Locale.ROOT)) {
                            hasGetter = true
                            break
                        }
                    }
                    if (!hasGetter) {
                        continue
                    }
                    when {
                        isEditText(variable) -> {
                            children[variableName] = "android.widget.EditText"
                        }
                        isTextView(variable) -> {
                            children[variableName] = "android.widget.TextView"
                        }
                        isImageView(variable) -> {
                            children[variableName] = "android.widget.ImageView"
                        }
                        isAndroidView(variable) -> {
                            children[variableName] = "android.view.View"
                        }
                    }
                }
            }
            val componentViewInfo = ComponentViewInfo(viewType, viewHolderClassName)
            componentViewInfo.children = children
            componentViewInfoList.add(componentViewInfo)
        }
        for (annotatedElement in roundEnv.getElementsAnnotatedWith(ComponentViewHolderBinder::class.java)) {
            val annotatedClass = annotatedElement as TypeElement
            val viewHolderClassName = (annotatedClass.interfaces[0] as Type.ClassType).typarams_field[0].toString()
            for (componentViewInfo in componentViewInfoList) {
                if (viewHolderClassName == componentViewInfo.viewHolder) {
                    componentViewInfo.viewBinder = (annotatedClass as ClassSymbol).fullname.toString()
                }
            }
        }
        for (annotatedElement in roundEnv.getElementsAnnotatedWith(ComponentViewClass::class.java)) {
            val annotatedClass = annotatedElement as TypeElement
            lateinit var viewHolderClassName: String
            try {
                annotatedElement.getAnnotation(ComponentViewClass::class.java).viewHolderClass
            } catch (exception: MirroredTypeException) {
                val typeMirror = exception.typeMirror
                viewHolderClassName = typeMirror.toString()
            }
            val useDefaultBinder = annotatedClass.getAnnotationsByType(ClickableComponentBinder::class.java).isNotEmpty()
            for (componentViewInfo in componentViewInfoList) {
                if (viewHolderClassName == componentViewInfo.viewHolder) {
                    componentViewInfo.componentView = (annotatedClass as ClassSymbol).fullname.toString()
                    componentViewInfo.viewTypeName = annotatedClass.simpleName.toString()
                    if (componentViewInfo.viewBinder == null && useDefaultBinder) {
                        componentViewInfo.viewBinder = DEFAULT_VIEW_BINDER
                    }
                }
            }
        }
        for (annotatedElement in roundEnv.getElementsAnnotatedWith(DefaultPresenter::class.java)) {
            val annotatedClass = annotatedElement as TypeElement
            var viewClassName: String? = null
            //TODO : validate class
            try {
                annotatedElement.getAnnotation(DefaultPresenter::class.java).viewClass
            } catch (exception: MirroredTypeException) {
                val typeMirror = exception.typeMirror
                viewClassName = typeMirror.toString()
            }
            for (componentViewInfo in componentViewInfoList) {
                if (viewClassName == componentViewInfo.componentView) {
                    componentViewInfo.defaultPresenter = (annotatedClass as ClassSymbol).fullname.toString()
                }
            }
        }
        val defaultPresenterConstructorMap = mutableMapOf<String, List<Pair<TypeName, String>>>()
        for (annotatedElement in roundEnv.getElementsAnnotatedWith(DefaultPresenterConstructor::class.java)) {
            val annotatedCtor = annotatedElement as MethodSymbol
            val ctorParams: List<Pair<TypeName, String>> = annotatedCtor.params
                    .map { param: VarSymbol ->
                        Pair<TypeName, String>(param.type.asTypeName(), param.name.toString())
                    }
            val presenterClass = annotatedCtor.owner as ClassSymbol
            defaultPresenterConstructorMap[presenterClass.fullname.toString()] = ctorParams
        }

        try {
            generateCode(appPackageName, outputPackageName, componentViewInfoList, defaultPresenterConstructorMap)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return true
    }

    private fun isAndroidView(variable: VariableElement): Boolean {
        val viewElement = processingEnv.elementUtils.getTypeElement("android.view.View")
        return processingEnv.typeUtils.isAssignable(variable.asType(), viewElement.asType())
    }

    private fun isTextView(variable: VariableElement): Boolean {
        val textViewElement = processingEnv.elementUtils.getTypeElement("android.widget.TextView")
        return processingEnv.typeUtils.isAssignable(variable.asType(), textViewElement.asType())
    }

    private fun isEditText(variable: VariableElement): Boolean {
        val textViewElement = processingEnv.elementUtils.getTypeElement("android.widget.EditText")
        return processingEnv.typeUtils.isAssignable(variable.asType(), textViewElement.asType())
    }

    private fun isImageView(variable: VariableElement): Boolean {
        val textViewElement = processingEnv.elementUtils.getTypeElement("android.widget.ImageView")
        return processingEnv.typeUtils.isAssignable(variable.asType(), textViewElement.asType())
    }

    private fun isValidClass(annotatedClass: TypeElement): Boolean {
        val applicationTypeElement = processingEnv.elementUtils.getTypeElement("androidx.recyclerview.widget.RecyclerView.ViewHolder")
        return processingEnv.typeUtils.isAssignable(annotatedClass.asType(), applicationTypeElement.asType())
    }

    @Throws(IOException::class) private fun generateCode(appPackageName: String, outputPackageName: String, componentInfoList: List<ComponentViewInfo>,
                                                         defaultPresenterContructorMap: Map<String, List<Pair<TypeName, String>>>) {
        val codeGenerator = CodeGenerator(appPackageName, componentInfoList, defaultPresenterContructorMap)
        val generatedClass = codeGenerator.generateComponentRegistry()

        val kotlinFile = generatedClass.name?.let {
            FileSpec.builder(outputPackageName, it).addType(generatedClass)
                    .addImport("androidx.recyclerview.widget", "RecyclerView")
                    .build()
        }

        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "\n\n\n component registry code \n\n" +
                "$generatedClass")

        try {
            kotlinFile?.writeTo(processingEnv.filer)
        } catch (e: Exception) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, e.toString())
        }

        val viewDelegates = codeGenerator.generateViewBaseClasses()
        for (viewDelegate in viewDelegates) {
            val viewDelegateKotlinFile = FileSpec.builder(viewDelegate.first, viewDelegate.second.name!!).addType(viewDelegate.second).build()
            viewDelegateKotlinFile.writeTo(processingEnv.filer)
        }
    }

    data class ComponentViewInfo internal constructor(val viewType: String, val viewHolder: String) {
        var viewTypeName: String? = null
        var defaultPresenter: String? = null
        var componentView: String? = null
        var viewBinder: String? = null
        var children: Map<String, String>? = null
    }

    companion object {
        const val DEFAULT_VIEW_BINDER = "com.xfinity.blueprint.view.ClickableComponentViewBinder"
    }
}