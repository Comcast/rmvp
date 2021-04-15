package com.xfinity.blueprint_compiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import java.util.ArrayList
import java.util.Locale

class CodeGenerator(private val appPackageName: String,
                    private val componentViewInfoList: List<BlueprintProcessor.ComponentViewInfo>? = null,
                    private val defaultPresenterConstructorMap: Map<String, List<Pair<TypeName, String>>>? = null) {

    fun generateComponentRegistry(): TypeSpec {
        val properties: MutableList<PropertySpec> = ArrayList()
        val companionProperties: MutableList<PropertySpec> = ArrayList()
        val componentViewWhenStatements: MutableList<String> = ArrayList()
        val defaultPresenterWhenStatements: MutableList<String> = ArrayList()
        componentViewWhenStatements.add("return when(viewType) {\n")
        defaultPresenterWhenStatements.add("return when(viewType) {\n")

        val starProjection = WildcardTypeName.producerOf(ClassName("kotlin", "Any").copy(true))
        val componentViewType: TypeName = ClassName("com.xfinity.blueprint.view", "ComponentView").plusParameter(starProjection)

        val nullableComponentPresenterType =
                ClassName("com.xfinity.blueprint.presenter", "ComponentPresenter")
                        .parameterizedBy(componentViewType, ClassName("com.xfinity.blueprint.model","ComponentModel"))
                        .copy(true)

        val getDefaultPresenterMethodbuilder1 = FunSpec.builder("getDefaultPresenter")
                .addModifiers(KModifier.PUBLIC)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("componentView", componentViewType)
                .addParameter("args", Object::class, KModifier.VARARG)
                .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build())
                .returns(nullableComponentPresenterType)

        val getDefaultPresenterMethodbuilder2 = FunSpec.builder("getDefaultPresenter")
                .addModifiers(KModifier.PUBLIC)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("viewType", INT)
                .addParameter("args", Object::class, KModifier.VARARG)
                .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build())
                .returns(nullableComponentPresenterType)

        getDefaultPresenterMethodbuilder1.addCode("return when(componentView) {\n")

        val contructorArgs: MutableList<Pair<TypeName, String>> = ArrayList()
        componentViewInfoList?.let {
            for (componentViewInfo in it) {
                val viewTypeFieldName = componentViewInfo.viewTypeName + "_VIEW_TYPE"
                val propertySpec = PropertySpec.builder(viewTypeFieldName, INT, KModifier.CONST)
                        .initializer("$appPackageName.R.layout.${componentViewInfo.viewType}").build()
                companionProperties.add(propertySpec)

                componentViewWhenStatements.add("$viewTypeFieldName -> ${componentViewInfo.componentView}() \n as? ComponentView<RecyclerView.ViewHolder>\n")
                if (componentViewInfo.defaultPresenter != null) {
                    getDefaultPresenterMethodbuilder1.addCode("is ${componentViewInfo.componentView} ->".trimIndent())
                    var returnStatement: String
                    val defaultPresenterConstructorArgs =
                            if (defaultPresenterConstructorMap != null && componentViewInfo.defaultPresenter != null) {
                                defaultPresenterConstructorMap[componentViewInfo.defaultPresenter!!]
                            } else {
                                null
                            }

                    returnStatement = if (defaultPresenterConstructorArgs == null) {
                        "${componentViewInfo.defaultPresenter}()"
                    } else {
                        val statementBuilder = StringBuilder("${componentViewInfo.defaultPresenter}(")
                        for (j in defaultPresenterConstructorArgs.indices) {
                            val argPair = defaultPresenterConstructorArgs[j]
                            val argName = argPair.second //arg name
                            statementBuilder.append(argName)
                            if (j < defaultPresenterConstructorArgs.size - 1) {
                                statementBuilder.append(", ")
                            } else {
                                statementBuilder.append(")")
                            }

                            //check if an arg with this name and type was already added to the ComponentRegistry's ctor, if
                            // not, add it
                            if (!contructorArgs.contains(argPair)) {
                                contructorArgs.add(argPair)
                            }
                        }
                        statementBuilder.toString()
                    }
                    getDefaultPresenterMethodbuilder1.addStatement("$returnStatement as? ComponentPresenter<ComponentView<*>, ComponentModel>?")
//                    getDefaultPresenterMethodbuilder1.addCode("}\n")
                    defaultPresenterWhenStatements.add("$viewTypeFieldName -> \n")
                    defaultPresenterWhenStatements.add("$returnStatement\n")
                }
            }

            defaultPresenterWhenStatements.add("else -> null")

        }

        val companion = TypeSpec.companionObjectBuilder()
                .addProperties(companionProperties)
                .build()

        val nullableComponentViewType = ClassName("com.xfinity.blueprint.view", "ComponentView").parameterizedBy(
        ClassName("androidx.recyclerview.widget", "RecyclerView.ViewHolder")).copy(true)

        val getComponentViewMethodbuilder = FunSpec.builder("getComponentView")
                .addModifiers(KModifier.PUBLIC)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("viewType", INT)
                .addAnnotation(AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build())
                .returns(nullableComponentViewType)

        for (statement in componentViewWhenStatements) {
            getComponentViewMethodbuilder.addCode(statement)
        }
        getComponentViewMethodbuilder.addCode("else -> null\n")
        getComponentViewMethodbuilder.addCode("}\n")

        getDefaultPresenterMethodbuilder1.addCode("else -> null\n")
        getDefaultPresenterMethodbuilder1.addCode("}\n")

        for (statement in defaultPresenterWhenStatements) {
            getDefaultPresenterMethodbuilder2.addCode(statement)
        }

        getDefaultPresenterMethodbuilder2.addCode("}\n")

        val componentRegistryConstructorBuilder = FunSpec.constructorBuilder()
        contructorArgs.sortWith(Comparator() { pair: Pair<TypeName, String>, pair1: Pair<TypeName, String> ->
            pair.first.toString().compareTo(pair1.first.toString(), ignoreCase = true)
        })
        for (argPair in contructorArgs) {
            properties.add(PropertySpec.builder(argPair.second, argPair.first, KModifier.PRIVATE).initializer(argPair.second).build())
            componentRegistryConstructorBuilder.addParameter(ParameterSpec.builder(argPair.second, argPair.first).build())
        }

        val classBuilder = TypeSpec.classBuilder("AppComponentRegistry")
                .addModifiers(KModifier.PUBLIC)
                .addSuperinterface(ClassName("com.xfinity.blueprint", "ComponentRegistry"))
                .addProperties(properties)
                .addType(companion)
                .primaryConstructor(componentRegistryConstructorBuilder.build())
                .addFunction(getComponentViewMethodbuilder.build())
                .addFunction(getDefaultPresenterMethodbuilder1.build())
                .addFunction(getDefaultPresenterMethodbuilder2.build())

        return classBuilder.build()
    }

    fun generateViewBaseClasses(): List<Pair<String, TypeSpec>> {
        val viewDelegatePairs: MutableList<Pair<String, TypeSpec>> = ArrayList()
        componentViewInfoList?.let {
            for (componentViewInfo in componentViewInfoList) {
                    val componentViewPackageName = componentViewInfo.componentView?.let {
                    componentViewInfo.componentView?.substring(0, it.lastIndexOf("."))
                }
                val viewHolderPackageName = componentViewInfo.viewHolder.substring(0, componentViewInfo.viewHolder.lastIndexOf("."))
                val viewHolderName = componentViewInfo.viewHolder.substring(componentViewInfo.viewHolder.lastIndexOf(".") + 1,
                        componentViewInfo.viewHolder.length)
                val viewHolderTypeName: TypeName = ClassName(viewHolderPackageName, viewHolderName)
                val viewBinderTypeName: TypeName = if (componentViewInfo.viewBinder != null && componentViewInfo.viewBinder == BlueprintProcessor.DEFAULT_VIEW_BINDER) {
                    ClassName("com.xfinity.blueprint.view", "ComponentViewBinder")
                } else {
                    ClassName("com.xfinity.blueprint.view", "ComponentViewBinder")
                            .plusParameter(viewHolderTypeName)
                }

                var viewBinderPropertySpec: PropertySpec?
                if (componentViewInfo.viewBinder != null) {
                    viewBinderPropertySpec = PropertySpec.builder("componentViewBinder", viewBinderTypeName,
                            KModifier.OVERRIDE).initializer("${componentViewInfo.viewBinder}()").build()
                } else {
                    viewBinderPropertySpec = PropertySpec.builder("componentViewBinder", viewBinderTypeName,
                            KModifier.OVERRIDE)
                            .initializer("com.xfinity.blueprint.view.ClickableComponentViewBinder()")
                            .build()
                }

                val viewHolderPropertySpec = PropertySpec.builder("viewHolder", viewHolderTypeName, KModifier.LATEINIT, KModifier.OVERRIDE).mutable().build()
                val viewGroupParam = ParameterSpec.builder("parent", ClassName("android.view", "ViewGroup")).build()
                val onCreateViewHolderMethod = FunSpec.builder("onCreateViewHolder")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(viewGroupParam)
                        .addStatement("val view = android.view.LayoutInflater.from(parent.getContext()).inflate(getViewType(), parent, false)")
                        .addStatement("return ${componentViewInfo.viewHolder}(view)")
                        .returns(viewHolderTypeName)
                        .build()

                val starProjection = WildcardTypeName.producerOf(ClassName("kotlin", "Any").copy(true))
                val componentViewTypeName = ClassName("com.xfinity.blueprint.view", "ComponentView")
                val wildcardComponentViewTypeName: TypeName = ClassName("com.xfinity.blueprint.view", "ComponentView").plusParameter(starProjection)

                val componentPresenterParam = ParameterSpec.builder("componentPresenter",
                        ClassName("com.xfinity.blueprint.presenter", "ComponentPresenter")
                                .plusParameter(wildcardComponentViewTypeName)
                                .plusParameter(ClassName("com.xfinity.blueprint.model", "ComponentModel"))).build()

                val viewHolderParam = ParameterSpec.builder("viewHolder", ClassName("androidx.recyclerview.widget", "RecyclerView").nestedClass("ViewHolder")).build()
                val positionParam = ParameterSpec.builder("position", INT).build()
                val parameterizedComponentViewTypeName: TypeName = componentViewTypeName.plusParameter(viewHolderTypeName)

                val onBindViewHolderMethodBuilder = FunSpec.builder("onBindViewHolder")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameters(listOf(componentPresenterParam, viewHolderParam, positionParam))
                        .addCode("""
    if (viewHolder is ${componentViewInfo.viewHolder}) {
    
    """.trimIndent())
                        .addStatement("this.viewHolder = viewHolder as ${componentViewInfo.viewHolder}")
                        .addCode("} else {\n")
                        .addStatement("throw IllegalArgumentException(\"You can only attach $viewHolderName to this view object\")")
                        .addCode("}\n")
                if (componentViewInfo.viewBinder != null) {
                    onBindViewHolderMethodBuilder.addStatement("componentViewBinder.bind(componentPresenter, this, this.viewHolder, position)")
                }
                val onBindViewHolderMethod = onBindViewHolderMethodBuilder.build()
                val getViewTypeMethod = FunSpec.builder("getViewType")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .returns(INT)
                        .addStatement("return $appPackageName.R.layout.${componentViewInfo.viewType}")
                        .build()
                val onBindViewHolderMethodFields = mutableListOf<PropertySpec>()
                if (viewBinderPropertySpec != null) {
                    onBindViewHolderMethodFields.add(viewBinderPropertySpec)
                }

                val methods = mutableListOf(
                        onCreateViewHolderMethod,
                        onBindViewHolderMethod,
                        getViewTypeMethod)

                componentViewInfo.children?.let {
                    for (child in it.keys) {
                        val type = it[child]
                        val childCapitalized = child.substring(0, 1).toUpperCase(Locale.getDefault()) + child.substring(1)
                        if (type == "android.widget.TextView") {
                            methods.add(getSetTextMethodSpec(child, childCapitalized))
                        }
                        if (type == "android.widget.ImageView") {
                            methods.add(getSetImageDrawableMethodSpec(child, childCapitalized))
                        }

                        methods.add(getMakeVisibleMethodSpec(child, childCapitalized))
                        methods.add(getMakeGoneMethodSpec(child, childCapitalized))
                        methods.add(getMakeInvisibleMethodSpec(child, childCapitalized))
                        methods.add(getSetBackgroundColorMethodSpec(child,childCapitalized))
                    }
                }
                onBindViewHolderMethodFields.add(viewHolderPropertySpec)
                val classBuilder = TypeSpec.classBuilder(componentViewInfo.viewTypeName + "Base")
                        .addModifiers(KModifier.PUBLIC, KModifier.OPEN)
                        .addSuperinterface(parameterizedComponentViewTypeName)
                        .addProperties(onBindViewHolderMethodFields)
                        .addFunctions(methods)
                viewDelegatePairs.add(Pair(componentViewPackageName ?: "", classBuilder.build()))
            }
        }
    return viewDelegatePairs
}

private fun getSetTextMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    val textParam = ParameterSpec.builder("text", ClassName("kotlin", "CharSequence")).build()

    //Warning:  this code assumes that fields all have getters, and that they're named getFieldName()
    return FunSpec.builder("set" + childNameCapitalized + "Text")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(textParam)
            .addStatement("viewHolder.$childName.text = text")
            .build()
}

private fun getMakeVisibleMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    return FunSpec.builder("make" + childNameCapitalized + "Visible")
            .addModifiers(KModifier.PUBLIC)
            .addStatement("viewHolder.$childName.visibility = android.view.View.VISIBLE")
            .build()
}

private fun getMakeGoneMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    return FunSpec.builder("make" + childNameCapitalized + "Gone")
            .addModifiers(KModifier.PUBLIC)
            .addStatement("viewHolder.$childName.visibility = android.view.View.GONE")
            .build()
}

private fun getMakeInvisibleMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    return FunSpec.builder("make" + childNameCapitalized + "Invisible")
            .addModifiers(KModifier.PUBLIC)
            .addStatement("viewHolder.$childName.visibility = android.view.View.INVISIBLE")
            .build()
}

private fun getSetBackgroundColorMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    val colorParam = ParameterSpec.builder("color", INT).build()
    return FunSpec.builder("set" + childNameCapitalized + "BackgroundColor")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(colorParam)
            .addStatement("viewHolder.$childName.setBackgroundColor(color)")
            .build()
}

private fun getSetImageDrawableMethodSpec(childName: String, childNameCapitalized: String): FunSpec {
    val imageParam = ParameterSpec.builder("drawable", ClassName("android.graphics.drawable", "Drawable")).build()
    return FunSpec.builder("set" + childNameCapitalized + "Image")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(imageParam)
            .addStatement("viewHolder.$childName.setImageDrawable(drawable)")
            .build()
}
}