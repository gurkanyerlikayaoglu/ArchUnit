package com.tngtech.archunit.core.importer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;

import com.google.common.base.Predicate;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.ForwardingCollection;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.AccessTarget.ConstructorCallTarget;
import com.tngtech.archunit.core.domain.AccessTarget.FieldAccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.InstanceofCheck;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClassList;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.domain.ThrowsDeclaration;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.DomainBuilders.FieldAccessTargetBuilder;
import com.tngtech.archunit.core.importer.DomainBuilders.MethodCallTargetBuilder;
import com.tngtech.archunit.core.importer.testexamples.FirstCheckedException;
import com.tngtech.archunit.core.importer.testexamples.OtherClass;
import com.tngtech.archunit.core.importer.testexamples.SecondCheckedException;
import com.tngtech.archunit.core.importer.testexamples.SomeClass;
import com.tngtech.archunit.core.importer.testexamples.SomeEnum;
import com.tngtech.archunit.core.importer.testexamples.arrays.ClassAccessingOneDimensionalArray;
import com.tngtech.archunit.core.importer.testexamples.arrays.ClassAccessingTwoDimensionalArray;
import com.tngtech.archunit.core.importer.testexamples.arrays.ClassUsedInArray;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsExternalMethod;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsMethodReturningArray;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsOtherConstructor;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsOtherMethod;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsOwnConstructor;
import com.tngtech.archunit.core.importer.testexamples.callimport.CallsOwnMethod;
import com.tngtech.archunit.core.importer.testexamples.callimport.ExternalInterfaceMethodCall;
import com.tngtech.archunit.core.importer.testexamples.callimport.ExternalOverriddenMethodCall;
import com.tngtech.archunit.core.importer.testexamples.callimport.ExternalSubTypeConstructorCall;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.BaseClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.CollectionInterface;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.GrandParentInterface;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.OtherInterface;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.OtherSubClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.ParentInterface;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SomeCollection;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SubClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SubInterface;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SubSubClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SubSubSubClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.SubSubSubSubClass;
import com.tngtech.archunit.core.importer.testexamples.classhierarchyimport.YetAnotherInterface;
import com.tngtech.archunit.core.importer.testexamples.complexexternal.ChildClass;
import com.tngtech.archunit.core.importer.testexamples.complexexternal.ParentClass;
import com.tngtech.archunit.core.importer.testexamples.complexmethodimport.ClassWithComplexMethod;
import com.tngtech.archunit.core.importer.testexamples.constructorimport.ClassWithComplexConstructor;
import com.tngtech.archunit.core.importer.testexamples.constructorimport.ClassWithSimpleConstructors;
import com.tngtech.archunit.core.importer.testexamples.constructorimport.ClassWithThrowingConstructor;
import com.tngtech.archunit.core.importer.testexamples.dependents.ClassHoldingDependencies;
import com.tngtech.archunit.core.importer.testexamples.dependents.FirstClassWithDependency;
import com.tngtech.archunit.core.importer.testexamples.dependents.SecondClassWithDependency;
import com.tngtech.archunit.core.importer.testexamples.diamond.ClassCallingDiamond;
import com.tngtech.archunit.core.importer.testexamples.diamond.ClassImplementingD;
import com.tngtech.archunit.core.importer.testexamples.diamond.InterfaceB;
import com.tngtech.archunit.core.importer.testexamples.diamond.InterfaceC;
import com.tngtech.archunit.core.importer.testexamples.diamond.InterfaceD;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ExternalFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ExternalShadowedFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ForeignFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ForeignFieldAccessFromConstructor;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ForeignFieldAccessFromStaticInitializer;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.ForeignStaticFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.MultipleFieldAccessInSameMethod;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.OwnFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccessimport.OwnStaticFieldAccess;
import com.tngtech.archunit.core.importer.testexamples.fieldaccesstointerfaces.ClassAccessingInterfaceFields;
import com.tngtech.archunit.core.importer.testexamples.fieldaccesstointerfaces.InterfaceWithFields;
import com.tngtech.archunit.core.importer.testexamples.fieldaccesstointerfaces.OtherInterfaceWithFields;
import com.tngtech.archunit.core.importer.testexamples.fieldaccesstointerfaces.ParentInterfaceWithFields;
import com.tngtech.archunit.core.importer.testexamples.fieldimport.ClassWithIntAndObjectFields;
import com.tngtech.archunit.core.importer.testexamples.fieldimport.ClassWithStringField;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalfieldaccess.AccessToSuperAndSubClassField;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalfieldaccess.SubClassWithAccessedField;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalfieldaccess.SuperClassWithAccessedField;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalmethodcall.CallOfSuperAndSubClassMethod;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalmethodcall.SubClassWithCalledMethod;
import com.tngtech.archunit.core.importer.testexamples.hierarchicalmethodcall.SuperClassWithCalledMethod;
import com.tngtech.archunit.core.importer.testexamples.innerclassimport.CalledClass;
import com.tngtech.archunit.core.importer.testexamples.innerclassimport.ClassWithInnerClass;
import com.tngtech.archunit.core.importer.testexamples.instanceofcheck.ChecksInstanceofInConstructor;
import com.tngtech.archunit.core.importer.testexamples.instanceofcheck.ChecksInstanceofInMethod;
import com.tngtech.archunit.core.importer.testexamples.instanceofcheck.ChecksInstanceofInStaticInitializer;
import com.tngtech.archunit.core.importer.testexamples.instanceofcheck.InstanceofChecked;
import com.tngtech.archunit.core.importer.testexamples.integration.ClassA;
import com.tngtech.archunit.core.importer.testexamples.integration.ClassBDependingOnClassA;
import com.tngtech.archunit.core.importer.testexamples.integration.ClassCDependingOnClassB_SuperClassOfX;
import com.tngtech.archunit.core.importer.testexamples.integration.ClassD;
import com.tngtech.archunit.core.importer.testexamples.integration.ClassXDependingOnClassesABCD;
import com.tngtech.archunit.core.importer.testexamples.integration.InterfaceOfClassX;
import com.tngtech.archunit.core.importer.testexamples.methodimport.ClassWithMultipleMethods;
import com.tngtech.archunit.core.importer.testexamples.methodimport.ClassWithObjectVoidAndIntIntSerializableMethod;
import com.tngtech.archunit.core.importer.testexamples.methodimport.ClassWithStringStringMethod;
import com.tngtech.archunit.core.importer.testexamples.methodimport.ClassWithThrowingMethod;
import com.tngtech.archunit.core.importer.testexamples.nestedimport.ClassWithNestedClass;
import com.tngtech.archunit.core.importer.testexamples.pathone.Class11;
import com.tngtech.archunit.core.importer.testexamples.pathone.Class12;
import com.tngtech.archunit.core.importer.testexamples.pathtwo.Class21;
import com.tngtech.archunit.core.importer.testexamples.pathtwo.Class22;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.AnnotationParameter;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.AnnotationToImport;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.ClassToImportOne;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.ClassToImportTwo;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.EnumToImport;
import com.tngtech.archunit.core.importer.testexamples.simpleimport.InterfaceToImport;
import com.tngtech.archunit.core.importer.testexamples.simplenames.SimpleNameExamples;
import com.tngtech.archunit.core.importer.testexamples.specialtargets.ClassCallingSpecialTarget;
import com.tngtech.archunit.core.importer.testexamples.syntheticimport.ClassWithSynthetics;
import com.tngtech.archunit.testutil.ArchConfigurationRule;
import com.tngtech.archunit.testutil.LogTestRule;
import com.tngtech.archunit.testutil.OutsideOfClassPathRule;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.apache.logging.log4j.Level;
import org.assertj.core.api.Condition;
import org.assertj.core.util.Objects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.newHashSet;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.JavaConstructor.CONSTRUCTOR_NAME;
import static com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType.GET;
import static com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType.SET;
import static com.tngtech.archunit.core.domain.JavaModifier.BRIDGE;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.core.domain.JavaModifier.PRIVATE;
import static com.tngtech.archunit.core.domain.JavaModifier.PROTECTED;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.core.domain.JavaModifier.STATIC;
import static com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC;
import static com.tngtech.archunit.core.domain.JavaModifier.TRANSIENT;
import static com.tngtech.archunit.core.domain.JavaModifier.VOLATILE;
import static com.tngtech.archunit.core.domain.JavaStaticInitializer.STATIC_INITIALIZER_NAME;
import static com.tngtech.archunit.core.domain.SourceTest.bytesAt;
import static com.tngtech.archunit.core.domain.SourceTest.urlOf;
import static com.tngtech.archunit.core.domain.TestUtils.MD5_SUM_DISABLED;
import static com.tngtech.archunit.core.domain.TestUtils.asClasses;
import static com.tngtech.archunit.core.domain.TestUtils.md5sumOf;
import static com.tngtech.archunit.core.domain.TestUtils.targetFrom;
import static com.tngtech.archunit.testutil.Assertions.assertThat;
import static com.tngtech.archunit.testutil.Assertions.assertThatAccess;
import static com.tngtech.archunit.testutil.Assertions.assertThatCall;
import static com.tngtech.archunit.testutil.Assertions.assertThatType;
import static com.tngtech.archunit.testutil.Assertions.assertThatTypes;
import static com.tngtech.archunit.testutil.ReflectionTestUtils.constructor;
import static com.tngtech.archunit.testutil.ReflectionTestUtils.field;
import static com.tngtech.archunit.testutil.ReflectionTestUtils.method;
import static com.tngtech.archunit.testutil.TestUtils.namesOf;
import static com.tngtech.java.junit.dataprovider.DataProviders.$;
import static com.tngtech.java.junit.dataprovider.DataProviders.$$;
import static com.tngtech.java.junit.dataprovider.DataProviders.testForEach;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

@RunWith(DataProviderRunner.class)
public class ClassFileImporterTest {
    @Rule
    public final OutsideOfClassPathRule outsideOfClassPath = new OutsideOfClassPathRule();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final LogTestRule logTest = new LogTestRule();
    @Rule
    public final IndependentClasspathRule independentClasspathRule = new IndependentClasspathRule();
    @Rule
    public final ArchConfigurationRule archConfigurationRule = new ArchConfigurationRule();

    @Test
    public void imports_simple_package() throws Exception {
        Set<String> expectedClassNames = Sets.newHashSet(
                ClassToImportOne.class.getName(),
                ClassToImportTwo.class.getName(),
                InterfaceToImport.class.getName(),
                EnumToImport.class.getName(),
                AnnotationToImport.class.getName(),
                AnnotationParameter.class.getName());

        Iterable<JavaClass> classes = classesIn("testexamples/simpleimport");

        assertThat(namesOf(classes)).containsOnlyElementsOf(expectedClassNames);
    }

    @Test
    public void imports_simple_class_details() throws Exception {
        ImportedClasses classes = classesIn("testexamples/simpleimport");
        JavaClass javaClass = classes.get(ClassToImportOne.class);

        assertThat(javaClass.isFullyImported()).isTrue();
        assertThat(javaClass.getName()).as("full name").isEqualTo(ClassToImportOne.class.getName());
        assertThat(javaClass.getSimpleName()).as("simple name").isEqualTo(ClassToImportOne.class.getSimpleName());
        assertThat(javaClass.getPackageName()).as("package name").isEqualTo(ClassToImportOne.class.getPackage().getName());
        assertThat(javaClass.getModifiers()).as("modifiers").containsOnly(JavaModifier.PUBLIC);
        assertThatType(javaClass.getSuperClass().get()).as("super class").matches(Object.class);
        assertThat(javaClass.getInterfaces()).as("interfaces").isEmpty();
        assertThat(javaClass.isInterface()).as("is interface").isFalse();
        assertThat(javaClass.isEnum()).as("is enum").isFalse();
        assertThat(javaClass.isAnnotation()).as("is annotation").isFalse();
        assertThat(javaClass.getEnclosingClass()).as("enclosing class").isAbsent();
        assertThat(javaClass.isTopLevelClass()).as("is top level class").isTrue();
        assertThat(javaClass.isNestedClass()).as("is nested class").isFalse();
        assertThat(javaClass.isMemberClass()).as("is member class").isFalse();
        assertThat(javaClass.isInnerClass()).as("is inner class").isFalse();
        assertThat(javaClass.isLocalClass()).as("is local class").isFalse();
        assertThat(javaClass.isAnonymousClass()).as("is anonymous class").isFalse();

        assertThat(classes.get(ClassToImportTwo.class).getModifiers()).containsOnly(JavaModifier.PUBLIC, JavaModifier.FINAL);
    }

    @Test
    public void imports_simple_enum() throws Exception {
        JavaClass javaClass = classesIn("testexamples/simpleimport").get(EnumToImport.class);

        assertThat(javaClass.getName()).as("full name").isEqualTo(EnumToImport.class.getName());
        assertThat(javaClass.getSimpleName()).as("simple name").isEqualTo(EnumToImport.class.getSimpleName());
        assertThat(javaClass.getPackageName()).as("package name").isEqualTo(EnumToImport.class.getPackage().getName());
        assertThat(javaClass.getModifiers()).as("modifiers").containsOnly(JavaModifier.PUBLIC, JavaModifier.FINAL);
        assertThatType(javaClass.getSuperClass().get()).as("super class").matches(Enum.class);
        assertThat(javaClass.getInterfaces()).as("interfaces").isEmpty();
        assertThatTypes(javaClass.getAllInterfaces()).matchInAnyOrder(Enum.class.getInterfaces());
        assertThat(javaClass.isInterface()).as("is interface").isFalse();
        assertThat(javaClass.isEnum()).as("is enum").isTrue();
        assertThat(javaClass.isAnnotation()).as("is annotation").isFalse();

        JavaEnumConstant constant = javaClass.getEnumConstant(EnumToImport.FIRST.name());
        assertThatType(constant.getDeclaringClass()).as("declaring class").isEqualTo(javaClass);
        assertThat(constant.name()).isEqualTo(EnumToImport.FIRST.name());
        assertThat(javaClass.getEnumConstants()).extractingResultOf("name").as("enum constant names")
                .containsOnly(EnumToImport.FIRST.name(), EnumToImport.SECOND.name());
    }

    @DataProvider
    public static Object[][] nested_static_classes() {
        return testForEach(ClassWithInnerClass.NestedStatic.class, ClassWithInnerClass.ImplicitlyNestedStatic.class);
    }

    @Test
    @UseDataProvider("nested_static_classes")
    public void imports_simple_static_nested_class(Class<?> nestedStaticClass) throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");
        JavaClass staticNestedClass = classes.get(nestedStaticClass);

        assertThatType(staticNestedClass).matches(nestedStaticClass);
        assertThat(staticNestedClass.isTopLevelClass()).as("is top level class").isFalse();
        assertThat(staticNestedClass.isNestedClass()).as("is nested class").isTrue();
        assertThat(staticNestedClass.isMemberClass()).as("is member class").isTrue();
        assertThat(staticNestedClass.isInnerClass()).as("is inner class").isFalse();
        assertThat(staticNestedClass.isLocalClass()).as("is local class").isFalse();
        assertThat(staticNestedClass.isAnonymousClass()).as("is anonymous class").isFalse();
    }

    @Test
    public void imports_simple_inner_class() throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");
        JavaClass innerClass = classes.get(ClassWithInnerClass.Inner.class);

        assertThatType(innerClass).matches(ClassWithInnerClass.Inner.class);
        assertThat(innerClass.isTopLevelClass()).as("is top level class").isFalse();
        assertThat(innerClass.isNestedClass()).as("is nested class").isTrue();
        assertThat(innerClass.isMemberClass()).as("is member class").isTrue();
        assertThat(innerClass.isInnerClass()).as("is inner class").isTrue();
        assertThat(innerClass.isLocalClass()).as("is local class").isFalse();
        assertThat(innerClass.isAnonymousClass()).as("is anonymous class").isFalse();
    }

    @Test
    public void imports_simple_anonymous_class() throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");
        JavaClass anonymousClass = classes.get(ClassWithInnerClass.class.getName() + "$1");

        assertThatType(anonymousClass).matches(Class.forName(anonymousClass.getName()));
        assertThat(anonymousClass.isTopLevelClass()).as("is top level class").isFalse();
        assertThat(anonymousClass.isNestedClass()).as("is nested class").isTrue();
        assertThat(anonymousClass.isMemberClass()).as("is member class").isFalse();
        assertThat(anonymousClass.isInnerClass()).as("is inner class").isTrue();
        assertThat(anonymousClass.isLocalClass()).as("is local class").isFalse();
        assertThat(anonymousClass.isAnonymousClass()).as("is anonymous class").isTrue();
    }

    @Test
    public void imports_simple_local_class() throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");
        JavaClass localClass = classes.get(ClassWithInnerClass.class.getName() + "$1LocalCaller");

        assertThatType(localClass).matches(Class.forName(localClass.getName()));
        assertThat(localClass.isTopLevelClass()).as("is top level class").isFalse();
        assertThat(localClass.isNestedClass()).as("is nested class").isTrue();
        assertThat(localClass.isMemberClass()).as("is member class").isFalse();
        assertThat(localClass.isInnerClass()).as("is inner class").isTrue();
        assertThat(localClass.isLocalClass()).as("is local class").isTrue();
        assertThat(localClass.isAnonymousClass()).as("is anonymous class").isFalse();
    }

    @Test
    public void imports_simple_class_names_of_generated_types_correctly() throws Exception {
        ImportedClasses classes = classesIn("testexamples/simplenames");

        assertSameSimpleNameOfArchUnitAndReflection(classes, SimpleNameExamples.class);
        assertSameSimpleNameOfArchUnitAndReflection(classes, SimpleNameExamples.Crazy$InnerClass$$LikeAByteCodeGenerator_might_create.class);
        assertSameSimpleNameOfArchUnitAndReflection(classes, SimpleNameExamples.class.getName() + "$1");
        assertSameSimpleNameOfArchUnitAndReflection(classes, SimpleNameExamples.class.getName() + "$1Crazy$LocalClass");
        assertSameSimpleNameOfArchUnitAndReflection(classes,
                SimpleNameExamples.Crazy$InnerClass$$LikeAByteCodeGenerator_might_create.NestedInnerClass$Also$$_crazy.class);
        assertSameSimpleNameOfArchUnitAndReflection(classes,
                SimpleNameExamples.Crazy$InnerClass$$LikeAByteCodeGenerator_might_create.class.getName() + "$1");
        assertSameSimpleNameOfArchUnitAndReflection(classes,
                SimpleNameExamples.Crazy$InnerClass$$LikeAByteCodeGenerator_might_create.class.getName() + "$1Crazy$$NestedLocalClass");
    }

    @Test
    public void imports_interfaces() throws Exception {
        JavaClass simpleInterface = classesIn("testexamples/simpleimport").get(InterfaceToImport.class);

        assertThat(simpleInterface.getName()).as("full name").isEqualTo(InterfaceToImport.class.getName());
        assertThat(simpleInterface.getSimpleName()).as("simple name").isEqualTo(InterfaceToImport.class.getSimpleName());
        assertThat(simpleInterface.getPackageName()).as("package name").isEqualTo(InterfaceToImport.class.getPackage().getName());
        assertThat(simpleInterface.getSuperClass()).as("super class").isAbsent();
        assertThat(simpleInterface.getInterfaces()).as("interfaces").isEmpty();
        assertThat(simpleInterface.isInterface()).as("is interface").isTrue();
        assertThat(simpleInterface.isEnum()).as("is enum").isFalse();
    }

    @Test
    public void imports_nested_classes() throws Exception {
        JavaClasses classes = classesIn("testexamples/nestedimport").classes;

        assertThatTypes(classes).matchInAnyOrder(
                ClassWithNestedClass.class,
                ClassWithNestedClass.NestedClass.class,
                ClassWithNestedClass.StaticNestedClass.class,
                ClassWithNestedClass.NestedInterface.class,
                ClassWithNestedClass.StaticNestedInterface.class,
                Class.forName(ClassWithNestedClass.class.getName() + "$PrivateNestedClass"));
    }

    @Test
    public void handles_static_modifier_of_nested_classes() throws Exception {
        JavaClasses classes = classesIn("testexamples/nestedimport").classes;

        assertThat(classes.get(ClassWithNestedClass.class).getModifiers()).as("modifiers of ClassWithNestedClass").doesNotContain(STATIC);
        assertThat(classes.get(ClassWithNestedClass.NestedClass.class).getModifiers()).as("modifiers of ClassWithNestedClass.NestedClass").doesNotContain(STATIC);
        assertThat(classes.get(ClassWithNestedClass.StaticNestedClass.class).getModifiers()).as("modifiers of ClassWithNestedClass.StaticNestedClass").contains(STATIC);
        assertThat(classes.get(ClassWithNestedClass.NestedInterface.class).getModifiers()).as("modifiers of ClassWithNestedClass.NestedInterface").contains(STATIC);
        assertThat(classes.get(ClassWithNestedClass.StaticNestedInterface.class).getModifiers()).as("modifiers of ClassWithNestedClass.StaticNestedInterface").contains(STATIC);
    }

    @Test
    public void handles_synthetic_modifiers() throws Exception {
        JavaClasses classes = classesIn("testexamples/syntheticimport").classes;

        JavaField syntheticField = getOnlyElement(classes.get(ClassWithSynthetics.ClassWithSyntheticField.class).getFields());
        assertThat(syntheticField.getModifiers()).as("modifiers of field in ClassWithSynthetics.ClassWithSyntheticField").contains(SYNTHETIC);

        JavaMethod syntheticMethod = getOnlyElement(classes.get(ClassWithSynthetics.ClassWithSyntheticMethod.class).getMethods());
        assertThat(syntheticMethod.getModifiers()).as("modifiers of method in ClassWithSynthetics.ClassWithSyntheticMethod").contains(SYNTHETIC);

        JavaMethod compareMethod = classes.get(ClassWithSynthetics.class).getMethod("compare", Object.class, Object.class);
        assertThat(compareMethod.getModifiers()).as("modifiers of bridge method in ClassWithSynthetics").contains(BRIDGE, SYNTHETIC);
    }

    @Test
    public void imports_jdk_classes() {
        JavaClasses classes = new ClassFileImporter().importClasses(File.class);

        assertThatTypes(classes).matchExactly(File.class);
    }

    @Test
    public void imports_jdk_packages() {
        JavaClasses classes = new ClassFileImporter().importPackagesOf(File.class);

        assertThatTypes(classes).contain(File.class);
    }

    @Test
    public void creates_JavaPackages_for_each_JavaClass() {
        JavaClasses classes = new ClassFileImporter().importPackagesOf(getClass());

        JavaPackage javaPackage = classes.get(SomeClass.class).getPackage();

        assertThat(javaPackage.containsClass(SomeEnum.class)).as("Package contains " + SomeEnum.class).isTrue();
        assertThatTypes(javaPackage.getParent().get().getClasses()).contain(getClass());
    }

    @DataProvider
    public static Object[][] array_types() {
        return testForEach(ClassAccessingOneDimensionalArray.class, ClassAccessingTwoDimensionalArray.class);
    }

    // we want to diverge from the Reflection API in this place, because it is way more useful for dependency checks,
    // if com.some.SomeArray[].getPackageName() reports 'com.some' instead of '' (which would be ArchUnit's equivalent of null)
    @Test
    @UseDataProvider("array_types")
    public void adds_package_of_component_type_to_arrays(Class<?> classAccessingArray) {
        JavaClass javaClass = new ClassFileImporter().importPackagesOf(classAccessingArray)
                .get(classAccessingArray);

        JavaClass arrayType = getOnlyElement(javaClass.getFieldAccessesFromSelf()).getTarget().getRawType();

        assertThat(arrayType.getPackageName()).isEqualTo(ClassUsedInArray.class.getPackage().getName());
        assertThat(arrayType.getPackage().getName()).isEqualTo(ClassUsedInArray.class.getPackage().getName());
    }

    @Test
    public void imports_fields() throws Exception {
        Set<JavaField> fields = classesIn("testexamples/fieldimport").getFields();

        assertThat(namesOf(fields)).containsOnly("stringField", "serializableField", "objectField");
        assertThat(findAnyByName(fields, "stringField"))
                .isEquivalentTo(field(ClassWithStringField.class, "stringField"));
        assertThat(findAnyByName(fields, "serializableField"))
                .isEquivalentTo(field(ClassWithIntAndObjectFields.class, "serializableField"));
        assertThat(findAnyByName(fields, "objectField"))
                .isEquivalentTo(field(ClassWithIntAndObjectFields.class, "objectField"));
    }

    @Test
    public void imports_primitive_fields() throws Exception {
        Set<JavaField> fields = classesIn("testexamples/primitivefieldimport").getFields();

        assertThatType(findAnyByName(fields, "aBoolean").getRawType()).matches(boolean.class);
        assertThatType(findAnyByName(fields, "anInt").getRawType()).matches(int.class);
        assertThatType(findAnyByName(fields, "aByte").getRawType()).matches(byte.class);
        assertThatType(findAnyByName(fields, "aChar").getRawType()).matches(char.class);
        assertThatType(findAnyByName(fields, "aShort").getRawType()).matches(short.class);
        assertThatType(findAnyByName(fields, "aLong").getRawType()).matches(long.class);
        assertThatType(findAnyByName(fields, "aFloat").getRawType()).matches(float.class);
        assertThatType(findAnyByName(fields, "aDouble").getRawType()).matches(double.class);
    }

    // NOTE: This provokes the scenario where the target type can't be determined uniquely due to a diamond
    //       scenario and thus a fallback to (primitive and array) type names by ASM descriptors occurs.
    //       Unfortunately those ASM type names for example are the canonical name instead of the class name.
    @Test
    public void imports_special_target_parameters() throws Exception {
        ImportedClasses classes = classesIn("testexamples/specialtargets");
        Set<JavaMethodCall> calls = classes.get(ClassCallingSpecialTarget.class).getMethodCallsFromSelf();

        assertThat(targetParametersOf(calls, "primitiveArgs")).matches(byte.class, long.class);
        assertThatType(returnTypeOf(calls, "primitiveReturnType")).matches(byte.class);
        assertThat(targetParametersOf(calls, "arrayArgs")).matches(byte[].class, Object[].class);
        assertThatType(returnTypeOf(calls, "primitiveArrayReturnType")).matches(short[].class);
        assertThatType(returnTypeOf(calls, "objectArrayReturnType")).matches(String[].class);
        assertThat(targetParametersOf(calls, "twoDimArrayArgs")).matches(float[][].class, Object[][].class);
        assertThatType(returnTypeOf(calls, "primitiveTwoDimArrayReturnType")).matches(double[][].class);
        assertThatType(returnTypeOf(calls, "objectTwoDimArrayReturnType")).matches(String[][].class);
    }

    @Test
    public void attaches_correct_owner_to_fields() throws Exception {
        Iterable<JavaClass> classes = classesIn("testexamples/fieldimport");

        for (JavaClass clazz : classes) {
            for (JavaField field : clazz.getFields()) {
                assertThatType(field.getOwner()).isSameAs(clazz);
            }
        }
    }

    @Test
    public void imports_fields_with_correct_modifiers() throws Exception {
        Set<JavaField> fields = classesIn("testexamples/modifierfieldimport").getFields();

        assertThat(findAnyByName(fields, "privateField").getModifiers()).containsOnly(PRIVATE);
        assertThat(findAnyByName(fields, "defaultField").getModifiers()).isEmpty();
        assertThat(findAnyByName(fields, "privateFinalField").getModifiers()).containsOnly(PRIVATE, FINAL);
        assertThat(findAnyByName(fields, "privateStaticField").getModifiers()).containsOnly(PRIVATE, STATIC);
        assertThat(findAnyByName(fields, "privateStaticFinalField").getModifiers()).containsOnly(PRIVATE, STATIC, FINAL);
        assertThat(findAnyByName(fields, "staticDefaultField").getModifiers()).containsOnly(STATIC);
        assertThat(findAnyByName(fields, "protectedField").getModifiers()).containsOnly(PROTECTED);
        assertThat(findAnyByName(fields, "protectedFinalField").getModifiers()).containsOnly(PROTECTED, FINAL);
        assertThat(findAnyByName(fields, "publicField").getModifiers()).containsOnly(PUBLIC);
        assertThat(findAnyByName(fields, "publicStaticFinalField").getModifiers()).containsOnly(PUBLIC, STATIC, FINAL);
        assertThat(findAnyByName(fields, "volatileField").getModifiers()).containsOnly(VOLATILE);
        assertThat(findAnyByName(fields, "synchronizedField").getModifiers()).containsOnly(TRANSIENT);
    }

    @Test
    public void imports_simple_methods_with_correct_parameters() throws Exception {
        Set<JavaMethod> methods = classesIn("testexamples/methodimport").getMethods();
        assertThat(methods).extractingResultOf("getDefaultValue").containsOnly(Optional.absent());

        assertThat(findAnyByName(methods, "createString")).isEquivalentTo(
                ClassWithStringStringMethod.class.getDeclaredMethod("createString", String.class));
        assertThat(findAnyByName(methods, "consume")).isEquivalentTo(
                ClassWithObjectVoidAndIntIntSerializableMethod.class.getDeclaredMethod("consume", Object.class));
        assertThat(findAnyByName(methods, "createSerializable")).isEquivalentTo(
                ClassWithObjectVoidAndIntIntSerializableMethod.class
                        .getDeclaredMethod("createSerializable", int.class, int.class));
    }

    @Test
    public void imports_complex_method_with_correct_parameters() throws Exception {
        JavaClass clazz = classesIn("testexamples/complexmethodimport").get(ClassWithComplexMethod.class);

        assertThat(clazz.getMethods()).as("Methods of %s", ClassWithComplexMethod.class.getSimpleName()).hasSize(1);

        Class<?>[] parameterTypes = {String.class, long.class, long.class, Serializable.class, Serializable.class};
        Method expectedMethod = ClassWithComplexMethod.class.getDeclaredMethod("complex", parameterTypes);

        assertThat(clazz.getMethod("complex", parameterTypes)).isEquivalentTo(expectedMethod);
        assertThat(clazz.tryGetMethod("complex", parameterTypes).get()).isEquivalentTo(expectedMethod);
        assertThat(clazz.getMethod("complex", Objects.namesOf(parameterTypes))).isEquivalentTo(expectedMethod);
        assertThat(clazz.tryGetMethod("complex", Objects.namesOf(parameterTypes)).get()).isEquivalentTo(expectedMethod);
    }

    @Test
    public void imports_methods_with_correct_return_types() throws Exception {
        Set<JavaCodeUnit> methods = classesIn("testexamples/methodimport").getCodeUnits();

        assertThatType(findAnyByName(methods, "createString").getRawReturnType())
                .as("Return type of method 'createString'").matches(String.class);
        assertThatType(findAnyByName(methods, "consume").getRawReturnType())
                .as("Return type of method 'consume'").matches(void.class);
        assertThatType(findAnyByName(methods, "createSerializable").getRawReturnType())
                .as("Return type of method 'createSerializable'").matches(Serializable.class);
    }

    @Test
    public void imports_methods_with_correct_throws_declarations() throws Exception {
        JavaMethod method = classesIn("testexamples/methodimport").get(ClassWithThrowingMethod.class).getMethod("throwExceptions");

        assertThat(method.getThrowsClause())
                .as("Throws types of method 'throwsExceptions'")
                .matches(FirstCheckedException.class, SecondCheckedException.class);
        assertThat(method.getExceptionTypes()).matches(FirstCheckedException.class, SecondCheckedException.class);
    }

    @Test
    public void imports_members_with_sourceCodeLocation() throws Exception {
        ImportedClasses importedClasses = classesIn("testexamples/methodimport");
        String sourceFileName = "ClassWithMultipleMethods.java";

        JavaClass javaClass = importedClasses.get(ClassWithMultipleMethods.class);
        assertThat(javaClass.getField("usage").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":0)");  // the byte code has no line number associated with a field
        assertThat(javaClass.getConstructor().getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":3)");  // auto-generated constructor seems to get line of class definition
        assertThat(javaClass.getStaticInitializer().get().getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":5)");  // auto-generated static initializer seems to get line of first static variable definition
        assertThat(javaClass.getMethod("methodDefinedInLine7").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":7)");
        assertThat(javaClass.getMethod("methodWithBodyStartingInLine10").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":10)");
        assertThat(javaClass.getMethod("emptyMethodDefinedInLine15").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":15)");
        assertThat(javaClass.getMethod("emptyMethodEndingInLine19").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":19)");

        javaClass = importedClasses.get(ClassWithMultipleMethods.InnerClass.class);
        assertThat(javaClass.getMethod("methodWithBodyStartingInLine24").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":24)");

        javaClass = importedClasses.get(ClassWithMultipleMethods.InnerClass.class.getName() + "$1");
        assertThat(javaClass.getMethod("run").getSourceCodeLocation())
                .hasToString("(" + sourceFileName + ":27)");
    }

    @Test
    public void imports_simple_constructors_with_correct_parameters() throws Exception {
        JavaClass clazz = classesIn("testexamples/constructorimport").get(ClassWithSimpleConstructors.class);

        assertThat(clazz.getConstructors()).as("Constructors").hasSize(3);

        Constructor<ClassWithSimpleConstructors> expectedConstructor = ClassWithSimpleConstructors.class.getDeclaredConstructor();
        assertThat(clazz.getConstructor()).isEquivalentTo(expectedConstructor);
        assertThat(clazz.tryGetConstructor().get()).isEquivalentTo(expectedConstructor);

        Class<?>[] parameterTypes = {Object.class};
        expectedConstructor = ClassWithSimpleConstructors.class.getDeclaredConstructor(parameterTypes);
        assertThat(clazz.getConstructor(parameterTypes)).isEquivalentTo(expectedConstructor);
        assertThat(clazz.getConstructor(Objects.namesOf(parameterTypes))).isEquivalentTo(expectedConstructor);
        assertThat(clazz.tryGetConstructor(parameterTypes).get()).isEquivalentTo(expectedConstructor);
        assertThat(clazz.tryGetConstructor(Objects.namesOf(parameterTypes)).get()).isEquivalentTo(expectedConstructor);

        parameterTypes = new Class[]{int.class, int.class};
        expectedConstructor = ClassWithSimpleConstructors.class.getDeclaredConstructor(parameterTypes);
        assertThat(clazz.getConstructor(parameterTypes)).isEquivalentTo(expectedConstructor);
        assertThat(clazz.getConstructor(Objects.namesOf(parameterTypes))).isEquivalentTo(expectedConstructor);
        assertThat(clazz.tryGetConstructor(parameterTypes).get()).isEquivalentTo(expectedConstructor);
        assertThat(clazz.tryGetConstructor(Objects.namesOf(parameterTypes)).get()).isEquivalentTo(expectedConstructor);
    }

    @Test
    public void imports_complex_constructor_with_correct_parameters() throws Exception {
        JavaClass clazz = classesIn("testexamples/constructorimport").get(ClassWithComplexConstructor.class);

        assertThat(clazz.getConstructors()).as("Constructors").hasSize(1);
        assertThat(clazz.getConstructor(String.class, long.class, long.class, Serializable.class, Serializable.class))
                .isEquivalentTo(ClassWithComplexConstructor.class.getDeclaredConstructor(
                        String.class, long.class, long.class, Serializable.class, Serializable.class));
    }

    @Test
    public void imports_constructor_with_correct_throws_declarations() throws Exception {
        JavaClass clazz = classesIn("testexamples/constructorimport").get(ClassWithThrowingConstructor.class);

        JavaConstructor constructor = getOnlyElement(clazz.getConstructors());
        assertThat(constructor.getThrowsClause()).as("Throws types of sole constructor")
                .matches(FirstCheckedException.class, SecondCheckedException.class);
        assertThat(constructor.getExceptionTypes()).matches(FirstCheckedException.class, SecondCheckedException.class);
    }

    @Test
    public void imports_interfaces_and_classes() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass baseClass = classes.get(BaseClass.class);
        JavaClass parentInterface = classes.get(ParentInterface.class);

        assertThat(baseClass.isInterface()).as(BaseClass.class.getSimpleName() + " is interface").isFalse();
        assertThat(parentInterface.isInterface()).as(ParentInterface.class.getSimpleName() + " is interface").isTrue();
    }

    @Test
    public void imports_base_class_in_class_hierarchy_correctly() throws Exception {
        JavaClass baseClass = classesIn("testexamples/classhierarchyimport").get(BaseClass.class);

        assertThat(baseClass.getConstructors()).as("Constructors of " + BaseClass.class.getSimpleName()).hasSize(2);
        assertThat(baseClass.getFields()).as("Fields of " + BaseClass.class.getSimpleName()).hasSize(1);
        assertThat(baseClass.getMethods()).as("Methods of " + BaseClass.class.getSimpleName()).hasSize(2);
        assertThat(baseClass.getStaticInitializer().get().getMethodCallsFromSelf().size())
                .as("Calls from %s.<clinit>()", BaseClass.class.getSimpleName()).isGreaterThan(0);
    }

    @Test
    public void imports_sub_class_in_class_hierarchy_correctly() throws Exception {
        JavaClass subClass = classesIn("testexamples/classhierarchyimport").get(SubClass.class);

        assertThat(subClass.getConstructors()).hasSize(3);
        assertThat(subClass.getFields()).hasSize(1);
        assertThat(subClass.getMethods()).hasSize(3);
        assertThat(subClass.getStaticInitializer().get().getMethodCallsFromSelf().size()).isGreaterThan(0);
    }

    @Test
    public void creates_relations_between_super_and_sub_classes() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass baseClass = classes.get(BaseClass.class);
        JavaClass subClass = classes.get(SubClass.class);
        JavaClass otherSubClass = classes.get(OtherSubClass.class);
        JavaClass subSubClass = classes.get(SubSubClass.class);
        JavaClass subSubSubClass = classes.get(SubSubSubClass.class);
        JavaClass subSubSubSubClass = classes.get(SubSubSubSubClass.class);

        assertThat(baseClass.getSuperClass().get().reflect()).isEqualTo(Object.class);
        assertThat(baseClass.getSubClasses()).containsOnly(subClass, otherSubClass);
        assertThat(baseClass.getAllSubClasses()).containsOnly(subClass, otherSubClass, subSubClass, subSubSubClass, subSubSubSubClass);
        assertThat(subClass.getSuperClass()).contains(baseClass);
        assertThat(subClass.getAllSubClasses()).containsOnly(subSubClass, subSubSubClass, subSubSubSubClass);
        assertThat(subSubClass.getSuperClass()).contains(subClass);
    }

    @Test
    public void creates_relations_between_classes_and_interfaces() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass baseClass = classes.get(BaseClass.class);
        JavaClass otherInterface = classes.get(OtherInterface.class);
        JavaClass subClass = classes.get(SubClass.class);
        JavaClass subInterface = classes.get(SubInterface.class);
        JavaClass otherSubClass = classes.get(OtherSubClass.class);
        JavaClass parentInterface = classes.get(ParentInterface.class);
        JavaClass grandParentInterface = classes.get(GrandParentInterface.class);
        JavaClass someCollection = classes.get(SomeCollection.class);
        JavaClass collectionInterface = classes.get(CollectionInterface.class);

        assertThat(baseClass.getInterfaces()).containsOnly(otherInterface);
        assertThat(baseClass.getAllInterfaces()).containsOnly(otherInterface, grandParentInterface);
        assertThat(subClass.getInterfaces()).containsOnly(subInterface);
        assertThat(subClass.getAllInterfaces()).containsOnly(
                subInterface, otherInterface, parentInterface, grandParentInterface);
        assertThat(otherSubClass.getInterfaces()).containsOnly(parentInterface);
        assertThat(otherSubClass.getAllInterfaces()).containsOnly(parentInterface, grandParentInterface, otherInterface);
        assertThat(someCollection.getInterfaces()).containsOnly(collectionInterface, otherInterface, subInterface);
        assertThat(someCollection.getAllInterfaces()).extractingResultOf("reflect").containsOnly(
                CollectionInterface.class, OtherInterface.class, SubInterface.class, ParentInterface.class,
                GrandParentInterface.class, Collection.class, Iterable.class);
    }

    @Test
    public void creates_relations_between_interfaces_and_interfaces() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass subInterface = classes.get(SubInterface.class);
        JavaClass parentInterface = classes.get(ParentInterface.class);
        JavaClass grandParentInterface = classes.get(GrandParentInterface.class);
        JavaClass collectionInterface = classes.get(CollectionInterface.class);

        assertThat(grandParentInterface.getAllInterfaces()).isEmpty();
        assertThat(parentInterface.getInterfaces()).containsOnly(grandParentInterface);
        assertThat(parentInterface.getAllInterfaces()).containsOnly(grandParentInterface);
        assertThat(subInterface.getInterfaces()).containsOnly(parentInterface);
        assertThat(subInterface.getAllInterfaces()).containsOnly(parentInterface, grandParentInterface);
        assertThat(collectionInterface.getInterfaces()).extractingResultOf("reflect").containsOnly(Collection.class);
    }

    @Test
    public void creates_relations_between_interfaces_and_sub_classes() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass baseClass = classes.get(BaseClass.class);
        JavaClass otherInterface = classes.get(OtherInterface.class);
        JavaClass subClass = classes.get(SubClass.class);
        JavaClass subSubClass = classes.get(SubSubClass.class);
        JavaClass subSubSubClass = classes.get(SubSubSubClass.class);
        JavaClass subSubSubSubClass = classes.get(SubSubSubSubClass.class);
        JavaClass subInterface = classes.get(SubInterface.class);
        JavaClass otherSubClass = classes.get(OtherSubClass.class);
        JavaClass parentInterface = classes.get(ParentInterface.class);
        JavaClass grandParentInterface = classes.get(GrandParentInterface.class);
        JavaClass someCollection = classes.get(SomeCollection.class);
        JavaClass collectionInterface = classes.get(CollectionInterface.class);

        assertThat(grandParentInterface.getSubClasses()).containsOnly(parentInterface, otherInterface);
        assertThat(grandParentInterface.getAllSubClasses()).containsOnly(
                parentInterface, subInterface, otherInterface,
                baseClass, subClass, otherSubClass, subSubClass, subSubSubClass, subSubSubSubClass, someCollection
        );
        assertThat(parentInterface.getSubClasses()).containsOnly(subInterface, otherSubClass);
        assertThat(parentInterface.getAllSubClasses()).containsOnly(
                subInterface, subClass, subSubClass, subSubSubClass, subSubSubSubClass, someCollection, otherSubClass);
        JavaClass collection = getOnlyElement(collectionInterface.getInterfaces());
        assertThat(collection.getAllSubClasses()).containsOnly(collectionInterface, someCollection);
    }

    @Test
    public void creates_superclass_and_interface_relations_missing_from_context() {
        JavaClass javaClass = new ClassFileImporter().importClass(SubSubSubSubClass.class);

        assertThat(javaClass.getAllSuperClasses()).extracting("name")
                .containsExactly(
                        SubSubSubClass.class.getName(),
                        SubSubClass.class.getName(),
                        SubClass.class.getName(),
                        BaseClass.class.getName(),
                        Object.class.getName());

        assertThat(javaClass.getAllInterfaces()).extracting("name")
                .containsOnly(
                        SubInterface.class.getName(),
                        YetAnotherInterface.class.getName(),
                        ParentInterface.class.getName(),
                        GrandParentInterface.class.getName(),
                        OtherInterface.class.getName());
    }

    @Test
    public void imports_enclosing_classes() throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");
        JavaClass classWithInnerClass = classes.get(ClassWithInnerClass.class);
        JavaClass innerClass = classes.get(ClassWithInnerClass.Inner.class);
        JavaClass anonymousClass = classes.get(ClassWithInnerClass.class.getName() + "$1");
        JavaClass localClass = classes.get(ClassWithInnerClass.class.getName() + "$1LocalCaller");
        JavaMethod calledTarget = getOnlyElement(classes.get(CalledClass.class).getMethods());

        assertThat(innerClass.getEnclosingClass()).contains(classWithInnerClass);
        assertThat(anonymousClass.getEnclosingClass()).contains(classWithInnerClass);
        assertThat(localClass.getEnclosingClass()).contains(classWithInnerClass);

        JavaMethodCall call = getOnlyElement(innerClass.getCodeUnitWithParameterTypes("call").getMethodCallsFromSelf());
        assertThatCall(call).isFrom("call").isTo(calledTarget).inLineNumber(31);

        call = getOnlyElement(anonymousClass.getCodeUnitWithParameterTypes("call").getMethodCallsFromSelf());
        assertThatCall(call).isFrom("call").isTo(calledTarget).inLineNumber(11);

        call = getOnlyElement(localClass.getCodeUnitWithParameterTypes("call").getMethodCallsFromSelf());
        assertThatCall(call).isFrom("call").isTo(calledTarget).inLineNumber(21);
    }

    @Test
    public void imports_overridden_methods_correctly() throws Exception {
        ImportedClasses classes = classesIn("testexamples/classhierarchyimport");
        JavaClass baseClass = classes.get(BaseClass.class);
        JavaClass subClass = classes.get(SubClass.class);

        assertThat(baseClass.getCodeUnitWithParameterTypes("getSomeField").getModifiers()).containsOnly(PROTECTED);
        assertThat(subClass.getCodeUnitWithParameterTypes("getSomeField").getModifiers()).containsOnly(PUBLIC);
    }

    @Test
    public void imports_own_get_field_access() throws Exception {
        JavaClass classWithOwnFieldAccess = classesIn("testexamples/fieldaccessimport").get(OwnFieldAccess.class);

        JavaMethod getStringValue = classWithOwnFieldAccess.getMethod("getStringValue");

        JavaFieldAccess access = getOnlyElement(getStringValue.getFieldAccesses());
        assertThatAccess(access)
                .isOfType(GET)
                .isFrom(getStringValue)
                .isTo("stringValue")
                .inLineNumber(8);
    }

    @Test
    public void imports_own_set_field_access() throws Exception {
        JavaClass classWithOwnFieldAccess = classesIn("testexamples/fieldaccessimport").get(OwnFieldAccess.class);

        JavaMethod setStringValue = classWithOwnFieldAccess.getMethod("setStringValue", String.class);

        JavaFieldAccess access = getOnlyElement(setStringValue.getFieldAccesses());
        assertThatAccess(access)
                .isOfType(SET)
                .isFrom(setStringValue)
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(12);
    }

    @Test
    public void imports_multiple_own_accesses() throws Exception {
        JavaClass classWithOwnFieldAccess = classesIn("testexamples/fieldaccessimport").get(OwnFieldAccess.class);

        Set<JavaFieldAccess> fieldAccesses = classWithOwnFieldAccess.getFieldAccessesFromSelf();

        assertThat(fieldAccesses).hasSize(4);
        assertThat(getOnly(fieldAccesses, "stringValue", GET).getLineNumber())
                .as("Line number of get stringValue").isEqualTo(8);
        assertThat(getOnly(fieldAccesses, "stringValue", SET).getLineNumber())
                .as("Line number of set stringValue").isEqualTo(12);
        assertThat(getOnly(fieldAccesses, "intValue", GET).getLineNumber())
                .as("Line number of get intValue").isEqualTo(16);
        assertThat(getOnly(fieldAccesses, "intValue", SET).getLineNumber())
                .as("Line number of set intValue").isEqualTo(20);
    }

    @Test
    public void imports_own_static_field_accesses() throws Exception {
        JavaClass classWithOwnFieldAccess = classesIn("testexamples/fieldaccessimport").get(OwnStaticFieldAccess.class);

        Set<JavaFieldAccess> accesses = classWithOwnFieldAccess.getFieldAccessesFromSelf();

        assertThat(accesses).hasSize(2);

        JavaFieldAccess getAccess = getOnly(accesses, "staticStringValue", GET);

        assertThatAccess(getAccess)
                .isFrom("getStaticStringValue")
                .isTo("staticStringValue")
                .inLineNumber(7);

        JavaFieldAccess setAccess = getOnly(accesses, "staticStringValue", SET);

        assertThatAccess(setAccess)
                .isFrom("setStaticStringValue", String.class)
                .isTo("staticStringValue")
                .inLineNumber(11);
    }

    @Test
    public void imports_other_field_accesses() throws Exception {
        ImportedClasses classes = classesIn("testexamples/fieldaccessimport");
        JavaClass classWithOwnFieldAccess = classes.get(OwnFieldAccess.class);
        JavaClass classWithForeignFieldAccess = classes.get(ForeignFieldAccess.class);

        Set<JavaFieldAccess> accesses = classWithForeignFieldAccess.getFieldAccessesFromSelf();

        assertThat(accesses).hasSize(4);

        assertThatAccess(getOnly(accesses, "stringValue", GET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("getStringFromOther"))
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(5);

        assertThatAccess(getOnly(accesses, "stringValue", SET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("setStringFromOther"))
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(9);

        assertThatAccess(getOnly(accesses, "intValue", GET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("getIntFromOther"))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(13);

        assertThatAccess(getOnly(accesses, "intValue", SET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("setIntFromOther"))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(17);
    }

    @Test
    public void imports_other_static_field_accesses() throws Exception {
        ImportedClasses classes = classesIn("testexamples/fieldaccessimport");
        JavaClass classWithOwnFieldAccess = classes.get(OwnStaticFieldAccess.class);
        JavaClass classWithForeignFieldAccess = classes.get(ForeignStaticFieldAccess.class);

        Set<JavaFieldAccess> accesses = classWithForeignFieldAccess.getFieldAccessesFromSelf();

        assertThat(accesses).as("Number of field accesses from " + classWithForeignFieldAccess.getName()).hasSize(2);

        assertThatAccess(getOnly(accesses, "staticStringValue", GET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("getStaticStringFromOther"))
                .isTo(classWithOwnFieldAccess.getField("staticStringValue"))
                .inLineNumber(5);

        assertThatAccess(getOnly(accesses, "staticStringValue", SET))
                .isFrom(classWithForeignFieldAccess.getCodeUnitWithParameterTypes("setStaticStringFromOther"))
                .isTo(classWithOwnFieldAccess.getField("staticStringValue"))
                .inLineNumber(9);
    }

    @Test
    public void imports_multiple_accesses_from_same_method() throws Exception {
        ImportedClasses classes = classesIn("testexamples/fieldaccessimport");
        JavaClass classWithOwnFieldAccess = classes.get(OwnFieldAccess.class);
        JavaClass multipleFieldAccesses = classes.get(MultipleFieldAccessInSameMethod.class);

        Set<JavaFieldAccess> accesses = multipleFieldAccesses.getFieldAccessesFromSelf();

        assertThat(accesses).as("Number of field accesses from " + multipleFieldAccesses.getName()).hasSize(5);

        Set<JavaFieldAccess> setStringValues = getByNameAndAccessType(accesses, "stringValue", SET);
        assertThat(setStringValues).hasSize(2);
        assertThat(targetsOf(setStringValues)).containsOnly(targetFrom(classWithOwnFieldAccess.getField("stringValue")));
        assertThat(lineNumbersOf(setStringValues)).containsOnly(6, 8);

        assertThatAccess(getOnly(accesses, "stringValue", GET))
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(7);

        assertThatAccess(getOnly(accesses, "intValue", GET))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(10);

        assertThatAccess(getOnly(accesses, "intValue", SET))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(11);
    }

    @Test
    public void imports_other_field_accesses_from_constructor() throws Exception {
        ImportedClasses classes = classesIn("testexamples/fieldaccessimport");
        JavaClass classWithOwnFieldAccess = classes.get(OwnFieldAccess.class);
        JavaClass fieldAccessFromConstructor = classes.get(ForeignFieldAccessFromConstructor.class);

        Set<JavaFieldAccess> accesses = fieldAccessFromConstructor.getFieldAccessesFromSelf();

        assertThat(accesses).as("Number of field accesses from " + fieldAccessFromConstructor.getName()).hasSize(2);

        assertThatAccess(getOnly(accesses, "stringValue", GET))
                .isFrom(fieldAccessFromConstructor.getCodeUnitWithParameterTypes(CONSTRUCTOR_NAME))
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(5);

        assertThatAccess(getOnly(accesses, "intValue", SET))
                .isFrom(fieldAccessFromConstructor.getCodeUnitWithParameterTypes(CONSTRUCTOR_NAME))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(6);
    }

    @Test
    public void imports_other_field_accesses_from_static_initializer() throws Exception {
        ImportedClasses classes = classesIn("testexamples/fieldaccessimport");
        JavaClass classWithOwnFieldAccess = classes.get(OwnFieldAccess.class);
        JavaClass fieldAccessFromInitializer = classes.get(ForeignFieldAccessFromStaticInitializer.class);

        Set<JavaFieldAccess> accesses = fieldAccessFromInitializer.getFieldAccessesFromSelf();

        assertThat(accesses).as("Number of field accesses from " + fieldAccessFromInitializer.getName()).hasSize(2);

        assertThatAccess(getOnly(accesses, "stringValue", GET))
                .isFrom(fieldAccessFromInitializer.getCodeUnitWithParameterTypes(STATIC_INITIALIZER_NAME))
                .isTo(classWithOwnFieldAccess.getField("stringValue"))
                .inLineNumber(5);

        assertThatAccess(getOnly(accesses, "intValue", SET))
                .isFrom(fieldAccessFromInitializer.getCodeUnitWithParameterTypes(STATIC_INITIALIZER_NAME))
                .isTo(classWithOwnFieldAccess.getField("intValue"))
                .inLineNumber(6);
    }

    @Test
    public void imports_external_field_access() throws Exception {
        JavaClass classWithExternalFieldAccess = classesIn("testexamples/fieldaccessimport").get(ExternalFieldAccess.class);

        JavaFieldAccess access = getOnlyElement(classWithExternalFieldAccess.getMethod("access").getFieldAccesses());

        assertThatAccess(access)
                .isFrom(classWithExternalFieldAccess.getCodeUnitWithParameterTypes("access"))
                .inLineNumber(8);

        assertThat(access.getTarget()).isEquivalentTo(field(ClassWithIntAndObjectFields.class, "objectField"));

        access = getOnlyElement(classWithExternalFieldAccess.getMethod("accessInheritedExternalField").getFieldAccesses());

        assertThatAccess(access)
                .isFrom(classWithExternalFieldAccess.getCodeUnitWithParameterTypes("accessInheritedExternalField"))
                .inLineNumber(12);

        assertThat(access.getTarget()).isEquivalentTo(field(ParentClass.class, "someParentField"));
    }

    @Test
    public void imports_external_field_access_with_shadowed_field() throws Exception {
        JavaClass classWithExternalFieldAccess = classesIn("testexamples/fieldaccessimport").get(ExternalShadowedFieldAccess.class);

        JavaFieldAccess access = getOnlyElement(classWithExternalFieldAccess.getFieldAccessesFromSelf());

        assertThatAccess(access)
                .isFrom(classWithExternalFieldAccess.getCodeUnitWithParameterTypes("accessField"))
                .inLineNumber(7);

        assertThat(access.getTarget()).isEquivalentTo(field(ChildClass.class, "someField"));
    }

    @Test
    public void imports_shadowed_and_superclass_field_access() throws Exception {
        ImportedClasses classes = classesIn("testexamples/hierarchicalfieldaccess");
        JavaClass classThatAccessesFieldOfSuperClass = classes.get(AccessToSuperAndSubClassField.class);
        JavaClass superClassWithAccessedField = classes.get(SuperClassWithAccessedField.class);
        JavaClass subClassWithAccessedField = classes.get(SubClassWithAccessedField.class);

        Set<JavaFieldAccess> accesses = classThatAccessesFieldOfSuperClass.getFieldAccessesFromSelf();

        assertThat(accesses).hasSize(2);
        JavaField field = superClassWithAccessedField.getField("field");
        FieldAccessTarget expectedSuperClassFieldAccess = new FieldAccessTargetBuilder()
                .withOwner(subClassWithAccessedField)
                .withName(field.getName())
                .withType(field.getRawType())
                .withField(Suppliers.ofInstance(Optional.of(field)))
                .build();
        assertThatAccess(getOnly(accesses, "field", GET))
                .isFrom("accessSuperClassField")
                .isTo(expectedSuperClassFieldAccess)
                .inLineNumber(5);
        assertThatAccess(getOnly(accesses, "maskedField", GET))
                .isFrom("accessSubClassField")
                .isTo(subClassWithAccessedField.getField("maskedField"))
                .inLineNumber(9);
    }

    @Test
    public void imports_field_accesses_to_fields_from_interfaces() throws Exception {
        Set<JavaFieldAccess> accesses = classesIn("testexamples/fieldaccesstointerfaces")
                .get(ClassAccessingInterfaceFields.class).getFieldAccessesFromSelf();

        assertThat(findAnyByName(accesses, "" + InterfaceWithFields.objectFieldOne).getTarget().resolveField().get())
                .isEquivalentTo(field(InterfaceWithFields.class, "" + InterfaceWithFields.objectFieldOne));
        assertThat(findAnyByName(accesses, "" + InterfaceWithFields.objectFieldTwo).getTarget().resolveField().get())
                .isEquivalentTo(field(InterfaceWithFields.class, "" + InterfaceWithFields.objectFieldTwo));
        assertThat(findAnyByName(accesses, "" + OtherInterfaceWithFields.otherObjectFieldOne).getTarget().resolveField().get())
                .isEquivalentTo(field(OtherInterfaceWithFields.class, "" + OtherInterfaceWithFields.otherObjectFieldOne));
        assertThat(findAnyByName(accesses, "" + OtherInterfaceWithFields.otherObjectFieldTwo).getTarget().resolveField().get())
                .isEquivalentTo(field(OtherInterfaceWithFields.class, "" + OtherInterfaceWithFields.otherObjectFieldTwo));
        assertThat(findAnyByName(accesses, "" + ParentInterfaceWithFields.parentObjectFieldOne).getTarget().resolveField().get())
                .isEquivalentTo(field(ParentInterfaceWithFields.class, "" + ParentInterfaceWithFields.parentObjectFieldOne));
        assertThat(findAnyByName(accesses, "" + ParentInterfaceWithFields.parentObjectFieldTwo).getTarget().resolveField().get())
                .isEquivalentTo(field(ParentInterfaceWithFields.class, "" + ParentInterfaceWithFields.parentObjectFieldTwo));
    }

    @Test
    public void imports_shadowed_and_superclass_method_calls() throws Exception {
        ImportedClasses classes = classesIn("testexamples/hierarchicalmethodcall");
        JavaClass classThatCallsMethodOfSuperClass = classes.get(CallOfSuperAndSubClassMethod.class);
        JavaClass superClassWithCalledMethod = classes.get(SuperClassWithCalledMethod.class);
        JavaClass subClassWithCalledMethod = classes.get(SubClassWithCalledMethod.class);

        Set<JavaMethodCall> calls = classThatCallsMethodOfSuperClass.getMethodCallsFromSelf();

        assertThat(calls).hasSize(2);

        JavaCodeUnit callSuperClassMethod = classThatCallsMethodOfSuperClass
                .getCodeUnitWithParameterTypes(CallOfSuperAndSubClassMethod.callSuperClassMethod);
        JavaMethod expectedSuperClassMethod = superClassWithCalledMethod.getMethod(SuperClassWithCalledMethod.method);
        MethodCallTarget expectedSuperClassCall = new MethodCallTargetBuilder()
                .withOwner(subClassWithCalledMethod)
                .withName(expectedSuperClassMethod.getName())
                .withParameters(expectedSuperClassMethod.getRawParameterTypes())
                .withReturnType(expectedSuperClassMethod.getRawReturnType())
                .withMethods(Suppliers.ofInstance(Collections.singleton(expectedSuperClassMethod)))
                .build();
        assertThatCall(getOnlyByCaller(calls, callSuperClassMethod))
                .isFrom(callSuperClassMethod)
                .isTo(expectedSuperClassCall)
                .inLineNumber(CallOfSuperAndSubClassMethod.callSuperClassLineNumber);

        JavaCodeUnit callSubClassMethod = classThatCallsMethodOfSuperClass
                .getCodeUnitWithParameterTypes(CallOfSuperAndSubClassMethod.callSubClassMethod);
        assertThatCall(getOnlyByCaller(calls, callSubClassMethod))
                .isFrom(callSubClassMethod)
                .isTo(subClassWithCalledMethod.getMethod(SubClassWithCalledMethod.maskedMethod))
                .inLineNumber(CallOfSuperAndSubClassMethod.callSubClassLineNumber);
    }

    @Test
    public void imports_constructor_calls_on_self() throws Exception {
        JavaClass classThatCallsOwnConstructor = classesIn("testexamples/callimport").get(CallsOwnConstructor.class);
        JavaCodeUnit caller = classThatCallsOwnConstructor.getCodeUnitWithParameterTypes("copy");

        Set<JavaConstructorCall> calls = classThatCallsOwnConstructor.getConstructorCallsFromSelf();

        assertThatCall(getOnlyByCaller(calls, caller))
                .isFrom(caller)
                .isTo(classThatCallsOwnConstructor.getConstructor(String.class))
                .inLineNumber(8);
    }

    @Test
    public void imports_method_calls_on_self() throws Exception {
        JavaClass classThatCallsOwnMethod = classesIn("testexamples/callimport").get(CallsOwnMethod.class);

        JavaMethodCall call = getOnlyElement(classThatCallsOwnMethod.getMethodCallsFromSelf());

        assertThatCall(call)
                .isFrom(classThatCallsOwnMethod.getCodeUnitWithParameterTypes("getString"))
                .isTo(classThatCallsOwnMethod.getMethod("string"))
                .inLineNumber(6);
    }

    @Test
    public void imports_constructor_calls_on_other() throws Exception {
        ImportedClasses classes = classesIn("testexamples/callimport");
        JavaClass classThatCallsOtherConstructor = classes.get(CallsOtherConstructor.class);
        JavaClass otherClass = classes.get(CallsOwnConstructor.class);
        JavaCodeUnit caller = classThatCallsOtherConstructor.getCodeUnitWithParameterTypes("createOther");

        Set<JavaConstructorCall> calls = classThatCallsOtherConstructor.getConstructorCallsFromSelf();

        assertThatCall(getOnlyByCaller(calls, caller))
                .isFrom(caller)
                .isTo(otherClass.getConstructor(String.class))
                .inLineNumber(5);
    }

    @Test
    public void imports_method_calls_on_other() throws Exception {
        ImportedClasses classes = classesIn("testexamples/callimport");
        JavaClass classThatCallsOtherMethod = classes.get(CallsOtherMethod.class);
        JavaClass other = classes.get(CallsOwnMethod.class);

        JavaMethodCall call = getOnlyElement(classThatCallsOtherMethod.getMethodCallsFromSelf());

        assertThatCall(call)
                .isFrom(classThatCallsOtherMethod.getCodeUnitWithParameterTypes("getFromOther"))
                .isTo(other.getMethod("getString"))
                .inLineNumber(7);
    }

    @Test
    public void imports_constructor_calls_on_external_class() throws Exception {
        JavaClass classThatCallsOwnConstructor = classesIn("testexamples/callimport").get(CallsOwnConstructor.class);
        JavaCodeUnit constructorCallingObjectInit = classThatCallsOwnConstructor.getConstructor(String.class);

        JavaConstructorCall objectInitCall = getOnlyElement(constructorCallingObjectInit.getConstructorCallsFromSelf());

        assertThatCall(objectInitCall)
                .isFrom(constructorCallingObjectInit)
                .inLineNumber(4);

        ConstructorCallTarget target = objectInitCall.getTarget();
        assertThat(target.getFullName()).isEqualTo(Object.class.getName() + ".<init>()");
        assertThat(reflect(target)).isEqualTo(Object.class.getConstructor());
    }

    @Test
    public void imports_constructor_calls_to_sub_type_constructor_on_external_class() throws Exception {
        JavaClass classWithExternalConstructorCall = classesIn("testexamples/callimport").get(ExternalSubTypeConstructorCall.class);

        assertConstructorCall(classWithExternalConstructorCall.getCodeUnitWithParameterTypes("call"), ChildClass.class, 9);
        assertConstructorCall(classWithExternalConstructorCall.getCodeUnitWithParameterTypes("newHashMap"), HashMap.class, 13);
    }

    private void assertConstructorCall(JavaCodeUnit call, Class<?> constructorOwner, int lineNumber) {
        JavaConstructorCall callToExternalClass =
                getOnlyElement(getByTargetOwner(call.getConstructorCallsFromSelf(), constructorOwner));

        assertThatCall(callToExternalClass)
                .isFrom(call)
                .inLineNumber(lineNumber);

        ConstructorCallTarget target = callToExternalClass.getTarget();
        assertThat(target.getFullName()).isEqualTo(constructorOwner.getName() + ".<init>()");
        assertThat(reflect(target)).isEqualTo(constructor(constructorOwner));
    }

    @Test
    public void imports_method_calls_on_external_class() throws Exception {
        JavaClass classThatCallsExternalMethod = classesIn("testexamples/callimport").get(CallsExternalMethod.class);

        JavaMethodCall call = getOnlyElement(classThatCallsExternalMethod.getMethodCallsFromSelf());

        assertThatCall(call)
                .isFrom(classThatCallsExternalMethod.getCodeUnitWithParameterTypes("getString"))
                .inLineNumber(7);

        MethodCallTarget target = call.getTarget();
        assertThat(target.getOwner().reflect()).isEqualTo(ArrayList.class);
        assertThat(target.getFullName()).isEqualTo(ArrayList.class.getName() + ".toString()");
    }

    @Test
    public void imports_method_calls_on_overridden_external_class() throws Exception {
        JavaClass classThatCallsExternalMethod = classesIn("testexamples/callimport").get(ExternalOverriddenMethodCall.class);

        JavaMethodCall call = getOnlyElement(classThatCallsExternalMethod.getMethodCallsFromSelf());

        assertThatCall(call)
                .isFrom(classThatCallsExternalMethod.getCodeUnitWithParameterTypes("call"))
                .inLineNumber(9);

        MethodCallTarget target = call.getTarget();
        assertThat(target.getFullName()).isEqualTo(ChildClass.class.getName() + ".overrideMe()");
        assertThat(getOnlyElement(target.resolve()).getFullName()).isEqualTo(ChildClass.class.getName() + ".overrideMe()");
        assertThat(reflect(target)).isEqualTo(method(ChildClass.class, "overrideMe"));
    }

    @Test
    public void imports_method_calls_on_external_interface_hierarchies() throws Exception {
        JavaClass classThatCallsExternalMethod = classesIn("testexamples/callimport").get(ExternalInterfaceMethodCall.class);

        JavaMethodCall call = getOnlyElement(classThatCallsExternalMethod.getMethodCallsFromSelf());

        assertThatCall(call)
                .isFrom(classThatCallsExternalMethod.getCodeUnitWithParameterTypes("call"))
                .inLineNumber(9);

        MethodCallTarget target = call.getTarget();
        assertThat(reflect(target)).isEqualTo(method(Map.class, "put", Object.class, Object.class));
    }

    @Test
    public void imports_non_unique_targets_for_diamond_scenarios() throws Exception {
        ImportedClasses diamondScenario = classesIn("testexamples/diamond");
        JavaClass classCallingDiamond = diamondScenario.get(ClassCallingDiamond.class);
        JavaClass diamondLeftInterface = diamondScenario.get(InterfaceB.class);
        JavaClass diamondRightInterface = diamondScenario.get(InterfaceC.class);
        JavaClass diamondPeakInterface = diamondScenario.get(InterfaceD.class);
        JavaClass diamondPeakClass = diamondScenario.get(ClassImplementingD.class);

        Set<JavaMethodCall> calls = classCallingDiamond.getMethodCallsFromSelf();

        assertThat(calls).hasSize(2);

        JavaCodeUnit callInterface = classCallingDiamond
                .getCodeUnitWithParameterTypes(ClassCallingDiamond.callInterface);
        JavaMethodCall callToInterface = getOnlyByCaller(calls, callInterface);
        assertThatCall(callToInterface)
                .isFrom(callInterface)
                .inLineNumber(ClassCallingDiamond.callInterfaceLineNumber);
        // NOTE: There is no java.lang.reflect.Method InterfaceD.implementMe(), because the method is inherited
        assertThat(callToInterface.getTarget().getName()).isEqualTo(InterfaceD.implementMe);
        assertThatType(callToInterface.getTarget().getOwner()).isEqualTo(diamondPeakInterface);
        assertThat(callToInterface.getTarget().getRawParameterTypes()).isEmpty();
        assertThat(callToInterface.getTarget().resolve()).extracting("fullName")
                .containsOnly(
                        diamondLeftInterface.getMethod(InterfaceB.implementMe).getFullName(),
                        diamondRightInterface.getMethod(InterfaceB.implementMe).getFullName());

        JavaCodeUnit callImplementation = classCallingDiamond
                .getCodeUnitWithParameterTypes(ClassCallingDiamond.callImplementation);
        assertThatCall(getOnlyByCaller(calls, callImplementation))
                .isFrom(callImplementation)
                .isTo(diamondPeakClass.getMethod(InterfaceD.implementMe))
                .inLineNumber(ClassCallingDiamond.callImplementationLineNumber);
    }

    @Test
    public void imports_method_calls_that_return_Arrays() throws Exception {
        JavaClass classThatCallsMethodReturningArray = classesIn("testexamples/callimport").get(CallsMethodReturningArray.class);

        MethodCallTarget target = getOnlyElement(classThatCallsMethodReturningArray.getMethodCallsFromSelf()).getTarget();
        assertThatType(target.getOwner()).matches(CallsMethodReturningArray.SomeEnum.class);
        assertThatType(target.getRawReturnType()).matches(CallsMethodReturningArray.SomeEnum[].class);
    }

    @Test
    public void dependency_target_classes_are_derived_correctly() throws Exception {
        ImportedClasses classes = classesIn("testexamples/integration");
        JavaClass javaClass = classes.get(ClassXDependingOnClassesABCD.class);
        Set<JavaClass> expectedTargetClasses = ImmutableSet.of(
                classes.get(ClassA.class),
                classes.get(ClassBDependingOnClassA.class),
                classes.get(ClassCDependingOnClassB_SuperClassOfX.class),
                classes.get(ClassD.class),
                classes.get(InterfaceOfClassX.class)
        );

        Set<JavaClass> targetClasses = new HashSet<>();
        for (Dependency dependency : withoutJavaLangTargets(javaClass.getDirectDependenciesFromSelf())) {
            targetClasses.add(dependency.getTargetClass());
        }

        assertThat(targetClasses).isEqualTo(expectedTargetClasses);
    }

    @Test
    public void getDirectDependencies_does_not_return_transitive_dependencies() throws Exception {
        ImportedClasses classes = classesIn("testexamples/integration");
        JavaClass javaClass = classes.get(ClassCDependingOnClassB_SuperClassOfX.class);
        JavaClass expectedTargetClass = classes.get(ClassBDependingOnClassA.class);

        Set<JavaClass> targetClasses = new HashSet<>();
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
            if (dependency.getTargetClass().getPackageName().contains("testexamples")) {
                targetClasses.add(dependency.getTargetClass());
            }
        }

        assertThat(targetClasses).containsOnly(expectedTargetClass);
    }

    @Test
    public void fields_know_their_accesses() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaFieldAccess> accesses = classHoldingDependencies.getField("someInt").getAccessesToSelf();
        Set<JavaFieldAccess> expected = ImmutableSet.<JavaFieldAccess>builder()
                .addAll(getByName(classHoldingDependencies.getFieldAccessesFromSelf(), "someInt"))
                .addAll(getByName(firstClassWithDependency.getFieldAccessesFromSelf(), "someInt"))
                .addAll(getByName(secondClassWithDependency.getFieldAccessesFromSelf(), "someInt"))
                .build();
        assertThat(accesses).as("Field Accesses to someInt").isEqualTo(expected);
    }

    @Test
    public void classes_know_the_field_accesses_to_them() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaFieldAccess> accesses = classHoldingDependencies.getFieldAccessesToSelf();
        Set<JavaFieldAccess> expected = ImmutableSet.<JavaFieldAccess>builder()
                .addAll(classHoldingDependencies.getFieldAccessesFromSelf())
                .addAll(firstClassWithDependency.getFieldAccessesFromSelf())
                .addAll(secondClassWithDependency.getFieldAccessesFromSelf())
                .build();
        assertThat(accesses).as("Field Accesses to class").isEqualTo(expected);
    }

    @Test
    public void classes_know_shadowed_field_accesses_to_themselves() {
        @SuppressWarnings("unused")
        class Base {
            String shadowed;
            String nonShadowed;
        }
        class Child extends Base {
            String shadowed;
        }
        @SuppressWarnings("unused")
        class Accessor {
            void access(Child child) {
                consume(child.shadowed);
                consume(child.nonShadowed);
            }

            void consume(String string) {
            }
        }
        JavaClasses classes = new ClassFileImporter().importClasses(Accessor.class, Base.class, Child.class);

        JavaFieldAccess access = getOnlyByCaller(
                classes.get(Base.class).getFieldAccessesToSelf(), classes.get(Accessor.class).getMethod("access", Child.class));
        assertThatAccess(access).isFrom("access", Child.class).isTo("nonShadowed");
        access = getOnlyByCaller(
                classes.get(Child.class).getFieldAccessesToSelf(), classes.get(Accessor.class).getMethod("access", Child.class));
        assertThatAccess(access).isFrom("access", Child.class).isTo("shadowed");
    }

    @Test
    public void methods_know_callers() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaMethodCall> calls = classHoldingDependencies.getMethod("setSomeInt", int.class).getCallsOfSelf();
        Set<JavaMethodCall> expected = ImmutableSet.<JavaMethodCall>builder()
                .addAll(getByName(classHoldingDependencies.getMethodCallsFromSelf(), "setSomeInt"))
                .addAll(getByName(firstClassWithDependency.getMethodCallsFromSelf(), "setSomeInt"))
                .addAll(getByName(secondClassWithDependency.getMethodCallsFromSelf(), "setSomeInt"))
                .build();
        assertThat(calls).as("Method calls to setSomeInt").isEqualTo(expected);
    }

    @Test
    public void classes_know_method_calls_to_themselves() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaMethodCall> calls = classHoldingDependencies.getMethodCallsToSelf();
        Set<JavaMethodCall> expected = ImmutableSet.<JavaMethodCall>builder()
                .addAll(classHoldingDependencies.getMethodCallsFromSelf())
                .addAll(getByTargetOwner(firstClassWithDependency.getMethodCallsFromSelf(), classHoldingDependencies))
                .addAll(getByTargetOwner(secondClassWithDependency.getMethodCallsFromSelf(), classHoldingDependencies))
                .build();
        assertThat(calls).as("Method calls to class").isEqualTo(expected);
    }

    @Test
    public void classes_know_overridden_method_calls_to_themselves() {
        @SuppressWarnings("unused")
        class Base {
            void overridden() {
            }

            void nonOverridden() {
            }
        }
        class Child extends Base {
            @Override
            void overridden() {
            }
        }
        @SuppressWarnings("unused")
        class Caller {
            void call(Child child) {
                child.overridden();
                child.nonOverridden();
            }
        }
        JavaClasses classes = new ClassFileImporter().importClasses(Caller.class, Base.class, Child.class);

        assertThatCall(getOnlyElement(classes.get(Base.class).getMethodCallsToSelf())).isFrom("call", Child.class).isTo("nonOverridden");
        assertThatCall(getOnlyElement(classes.get(Child.class).getMethodCallsToSelf())).isFrom("call", Child.class).isTo("overridden");
    }

    @Test
    public void constructors_know_callers() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        JavaConstructor targetConstructur = classHoldingDependencies.getConstructor();
        Set<JavaConstructorCall> calls = targetConstructur.getCallsOfSelf();
        Set<JavaConstructorCall> expected = ImmutableSet.<JavaConstructorCall>builder()
                .addAll(getByTarget(classHoldingDependencies.getConstructorCallsFromSelf(), targetConstructur))
                .addAll(getByTarget(firstClassWithDependency.getConstructorCallsFromSelf(), targetConstructur))
                .addAll(getByTarget(secondClassWithDependency.getConstructorCallsFromSelf(), targetConstructur))
                .build();
        assertThat(calls).as("Default Constructor calls to ClassWithDependents").isEqualTo(expected);
    }

    @Test
    public void classes_know_constructor_calls_to_themselves() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaConstructorCall> calls = classHoldingDependencies.getConstructorCallsToSelf();
        Set<JavaConstructorCall> expected = ImmutableSet.<JavaConstructorCall>builder()
                .addAll(getByTargetOwner(classHoldingDependencies.getConstructorCallsFromSelf(), classHoldingDependencies))
                .addAll(getByTargetOwner(firstClassWithDependency.getConstructorCallsFromSelf(), classHoldingDependencies))
                .addAll(getByTargetOwner(secondClassWithDependency.getConstructorCallsFromSelf(), classHoldingDependencies))
                .build();
        assertThat(calls).as("Constructor calls to ClassWithDependents").isEqualTo(expected);
    }

    @Test
    public void classes_know_constructor_calls_to_themselves_for_subclass_default_constructors() {
        // For constructors it's impossible to be accessed via a subclass,
        // since the byte code always holds an explicitly declared constructor.
        // Thus we do expect a call to the constructor of the subclass and one from subclass to super class
        @SuppressWarnings("unused")
        class Base {
            Base() {
            }
        }
        class Child extends Base {
        }
        @SuppressWarnings("unused")
        class Caller {
            void call() {
                new Child();
            }
        }
        JavaClasses classes = new ClassFileImporter().importClasses(Caller.class, Base.class, Child.class);

        assertThatCall(getOnlyElement(classes.get(Base.class).getConstructorCallsToSelf())).isFrom(Child.class, CONSTRUCTOR_NAME, getClass()).isTo(Base.class);
        assertThatCall(getOnlyElement(classes.get(Child.class).getConstructorCallsToSelf())).isFrom(Caller.class, "call").isTo(Child.class);
    }

    @Test
    public void classes_know_accesses_to_themselves() throws Exception {
        ImportedClasses classes = classesIn("testexamples/dependents");
        JavaClass classHoldingDependencies = classes.get(ClassHoldingDependencies.class);
        JavaClass firstClassWithDependency = classes.get(FirstClassWithDependency.class);
        JavaClass secondClassWithDependency = classes.get(SecondClassWithDependency.class);

        Set<JavaAccess<?>> accesses = classHoldingDependencies.getAccessesToSelf();
        Set<JavaAccess<?>> expected = ImmutableSet.<JavaAccess<?>>builder()
                .addAll(getByTargetOwner(classHoldingDependencies.getAccessesFromSelf(), classHoldingDependencies))
                .addAll(getByTargetOwner(firstClassWithDependency.getAccessesFromSelf(), classHoldingDependencies))
                .addAll(getByTargetOwner(secondClassWithDependency.getAccessesFromSelf(), classHoldingDependencies))
                .build();
        assertThat(accesses).as("Accesses to ClassWithDependents").isEqualTo(expected);
    }

    @Test
    public void classes_know_which_fields_have_their_type() {
        JavaClasses classes = new ClassFileImporter().importClasses(SomeClass.class, OtherClass.class, SomeEnum.class);

        assertThat(classes.get(SomeEnum.class).getFieldsWithTypeOfSelf())
                .extracting("name").contains("other", "someEnum");
    }

    @Test
    public void classes_know_which_methods_have_their_type_as_parameter() {
        JavaClasses classes = new ClassFileImporter().importClasses(SomeClass.class, OtherClass.class, SomeEnum.class);

        assertThat(classes.get(SomeEnum.class).getMethodsWithParameterTypeOfSelf())
                .extracting("name").contains("methodWithSomeEnumParameter", "otherMethodWithSomeEnumParameter");
    }

    @Test
    public void classes_know_which_methods_have_their_type_as_return_type() {
        JavaClasses classes = new ClassFileImporter().importClasses(SomeClass.class, OtherClass.class, SomeEnum.class);

        assertThat(classes.get(SomeEnum.class).getMethodsWithReturnTypeOfSelf())
                .extracting("name").contains("methodWithSomeEnumReturnType", "otherMethodWithSomeEnumReturnType");
    }

    @Test
    public void classes_know_which_method_throws_clauses_contain_their_type() {
        JavaClasses classes = new ClassFileImporter().importClasses(ClassWithThrowingMethod.class, FirstCheckedException.class);

        Set<ThrowsDeclaration<JavaMethod>> throwsDeclarations = classes.get(FirstCheckedException.class).getMethodThrowsDeclarationsWithTypeOfSelf();
        assertThatType(getOnlyElement(throwsDeclarations).getDeclaringClass()).matches(ClassWithThrowingMethod.class);
        assertThat(classes.get(FirstCheckedException.class).getConstructorsWithParameterTypeOfSelf()).isEmpty();
    }

    @Test
    public void classes_know_which_constructors_have_their_type_as_parameter() {
        JavaClasses classes = new ClassFileImporter().importClasses(SomeClass.class, OtherClass.class, SomeEnum.class);

        assertThat(classes.get(SomeEnum.class).getConstructorsWithParameterTypeOfSelf())
                .extracting("owner").extracting("name")
                .contains(SomeClass.class.getName(), OtherClass.class.getName());
    }

    @Test
    public void classes_know_which_constructor_throws_clauses_contain_their_type() {
        JavaClasses classes = new ClassFileImporter().importClasses(ClassWithThrowingConstructor.class, FirstCheckedException.class);

        Set<ThrowsDeclaration<JavaConstructor>> throwsDeclarations =
                classes.get(FirstCheckedException.class).getConstructorsWithThrowsDeclarationTypeOfSelf();
        assertThatType(getOnlyElement(throwsDeclarations).getDeclaringClass()).matches(ClassWithThrowingConstructor.class);
        assertThat(classes.get(FirstCheckedException.class).getMethodThrowsDeclarationsWithTypeOfSelf()).isEmpty();
    }

    @Test
    public void classes_know_which_instanceof_checks_check_their_type() {
        JavaClass clazz = new ClassFileImporter().importPackagesOf(InstanceofChecked.class).get(InstanceofChecked.class);

        Set<JavaClass> origins = new HashSet<>();
        for (InstanceofCheck instanceofCheck : clazz.getInstanceofChecksWithTypeOfSelf()) {
            origins.add(instanceofCheck.getOwner().getOwner());
        }
        assertThatTypes(origins).matchInAnyOrder(ChecksInstanceofInMethod.class, ChecksInstanceofInConstructor.class, ChecksInstanceofInStaticInitializer.class);
    }

    @Test
    public void reflect_works() throws Exception {
        ImportedClasses classes = classesIn("testexamples/innerclassimport");

        JavaClass calledClass = classes.get(CalledClass.class);
        assertThat(calledClass.reflect()).isEqualTo(CalledClass.class);
        assertThat(calledClass.getField("someString").reflect()).isEqualTo(field(CalledClass.class, "someString"));
        assertThat(calledClass.getConstructor().reflect()).isEqualTo(constructor(CalledClass.class));
        assertThat(calledClass.getConstructor(String.class).reflect()).isEqualTo(constructor(CalledClass.class, String.class));
        assertThat(calledClass.getCodeUnitWithParameterTypes(CONSTRUCTOR_NAME, String.class).reflect())
                .isEqualTo(constructor(CalledClass.class, String.class));

        JavaClass innerClass = classes.get(ClassWithInnerClass.Inner.class);
        assertThat(innerClass.reflect()).isEqualTo(ClassWithInnerClass.Inner.class);
        assertThat(innerClass.getMethod("call").reflect())
                .isEqualTo(method(ClassWithInnerClass.Inner.class, "call"));
    }

    @Test
    public void imports_urls_of_files() {
        Set<URL> urls = newHashSet(urlOf(ClassToImportOne.class), urlOf(ClassWithNestedClass.class));

        Set<JavaClass> classesFoundAtUrls = new HashSet<>();
        for (JavaClass javaClass : new ClassFileImporter().importUrls(urls)) {
            if (!Object.class.getName().equals(javaClass.getName())) {
                classesFoundAtUrls.add(javaClass);
            }
        }
        assertThat(classesFoundAtUrls).as("Number of classes at the given URLs").hasSize(2);
    }

    @Test
    public void imports_urls_of_folders() throws Exception {
        File testexamplesFolder = new File(new File(urlOf(getClass()).toURI()).getParentFile(), "testexamples");

        JavaClasses javaClasses = new ClassFileImporter().importUrl(testexamplesFolder.toURI().toURL());

        assertThatTypes(javaClasses).contain(SomeClass.class, OtherClass.class);
    }

    @Test
    public void imports_urls_of_jars() {
        Set<URL> urls = newHashSet(urlOf(Test.class), urlOf(RunWith.class));
        assumeTrue("We can't completely ensure that this will always be taken from a JAR file, though it's very likely",
                "jar".equals(urls.iterator().next().getProtocol()));

        JavaClasses classes = new ClassFileImporter().importUrls(urls)
                .that(DescribedPredicate.not(type(Annotation.class))); // NOTE @Test and @RunWith implement Annotation.class

        assertThat(classes).as("Number of classes at the given URLs").hasSize(2);
    }

    @Test
    public void imports_classes_outside_of_the_classpath() throws IOException {
        Path targetDir = outsideOfClassPath
                .onlyKeep(not(containsPattern("^Missing.*")))
                .setUp(getClass().getResource("testexamples/outsideofclasspath"));

        JavaClasses classes = new ClassFileImporter().importPath(targetDir);

        assertThat(classes).hasSize(5);
        assertThat(classes).extracting("name").containsOnly(
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.ChildClass",
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.MiddleClass",
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.ExistingDependency",
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.ChildClass$MySeed",
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.ExistingDependency$GimmeADescription"
        );

        JavaClass middleClass = findAnyByName(classes,
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.MiddleClass");
        assertThat(middleClass.getSimpleName()).as("simple name").isEqualTo("MiddleClass");
        assertThat(middleClass.isInterface()).as("is interface").isFalse();
        assertThatCall(findAnyByName(middleClass.getMethodCallsFromSelf(), "println"))
                .isFrom(middleClass.getMethod("overrideMe"))
                .isTo(targetWithFullName(String.format("%s.println(%s)", PrintStream.class.getName(), String.class.getName())))
                .inLineNumber(12);
        assertThatCall(findAnyByName(middleClass.getMethodCallsFromSelf(), "getSomeString"))
                .isFrom(middleClass.getMethod("overrideMe"))
                .isTo(targetWithFullName(
                        "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.MissingDependency.getSomeString()"))
                .inLineNumber(12);

        JavaClass gimmeADescription = findAnyByName(classes,
                "com.tngtech.archunit.core.importer.testexamples.outsideofclasspath.ExistingDependency$GimmeADescription");
        assertThat(gimmeADescription.getSimpleName()).as("simple name").isEqualTo("GimmeADescription");
        assertThat(gimmeADescription.isInterface()).as("is interface").isTrue();
    }

    @Test
    public void resolve_missing_dependencies_from_classpath_can_be_toogled() throws Exception {
        ArchConfiguration.get().unsetClassResolver();
        ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(true);
        JavaClass clazz = classesIn("testexamples/simpleimport").get(ClassToImportOne.class);

        assertThat(clazz.getSuperClass().get().getMethods()).isNotEmpty();

        ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
        clazz = classesIn("testexamples/simpleimport").get(ClassToImportOne.class);

        assertThat(clazz.getSuperClass().get().getMethods()).isEmpty();
    }

    @DataProvider
    public static Object[][] classes_not_fully_imported() {
        class Element {
        }
        @SuppressWarnings("unused")
        class DependsOnArray {
            Element[] array;
        }

        return ArchConfigurationRule.resetConfigurationAround(new Callable<Object[][]>() {
            @Override
            public Object[][] call() {
                ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(true);
                JavaClass resolvedFromClasspath = new ClassFileImporter().importClasses(DependsOnArray.class)
                        .get(DependsOnArray.class).getField("array").getRawType().getComponentType();

                ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(false);
                JavaClass stub = new ClassFileImporter().importClasses(DependsOnArray.class)
                        .get(DependsOnArray.class).getField("array").getRawType().getComponentType();

                return $$(
                        $("Resolved from classpath", resolvedFromClasspath),
                        $("Stub class", stub)
                );
            }
        });
    }

    @Test
    @UseDataProvider("classes_not_fully_imported")
    public void classes_not_fully_imported_have_flag_fullyImported_false_and_empty_dependencies(@SuppressWarnings("unused") String description, JavaClass notFullyImported) {
        assertThat(notFullyImported.isFullyImported()).isFalse();
        assertThat(notFullyImported.getDirectDependenciesFromSelf()).isEmpty();
        assertThat(notFullyImported.getDirectDependenciesToSelf()).isEmpty();
        assertThat(notFullyImported.getFieldAccessesToSelf()).isEmpty();
        assertThat(notFullyImported.getMethodCallsToSelf()).isEmpty();
        assertThat(notFullyImported.getConstructorCallsToSelf()).isEmpty();
        assertThat(notFullyImported.getAccessesToSelf()).isEmpty();
        assertThat(notFullyImported.getFieldsWithTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getMethodsWithParameterTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getMethodsWithReturnTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getMethodThrowsDeclarationsWithTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getConstructorsWithParameterTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getConstructorsWithThrowsDeclarationTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getAnnotationsWithTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getAnnotationsWithParameterTypeOfSelf()).isEmpty();
        assertThat(notFullyImported.getInstanceofChecksWithTypeOfSelf()).isEmpty();
    }

    @Test
    public void import_is_resilient_against_broken_class_files() throws Exception {
        Class<?> expectedClass = getClass();

        File folder = temporaryFolder.newFolder();
        copyClassFile(expectedClass, folder);
        Files.write(new File(folder, "Evil.class").toPath(), "broken".getBytes(UTF_8));

        logTest.watch(ClassFileProcessor.class, Level.WARN);

        JavaClasses classes = new ClassFileImporter().importPath(folder.toPath());

        assertThatTypes(classes).matchExactly(expectedClass);
        logTest.assertLogMessage(Level.WARN, "Evil.class");
    }

    @Test
    public void class_has_source_of_import() throws Exception {
        ArchConfiguration.get().setMd5InClassSourcesEnabled(true);

        JavaClass clazzFromFile = new ClassFileImporter().importClass(ClassToImportOne.class);
        Source source = clazzFromFile.getSource().get();
        assertThat(source.getUri()).isEqualTo(urlOf(ClassToImportOne.class).toURI());
        assertThat(source.getFileName()).contains(ClassToImportOne.class.getSimpleName() + ".java");
        assertThat(source.getMd5sum()).isEqualTo(md5sumOf(bytesAt(urlOf(ClassToImportOne.class))));

        clazzFromFile = new ClassFileImporter().importClass(ClassWithInnerClass.Inner.class);
        source = clazzFromFile.getSource().get();
        assertThat(source.getUri()).isEqualTo(urlOf(ClassWithInnerClass.Inner.class).toURI());
        assertThat(source.getFileName()).contains(ClassWithInnerClass.class.getSimpleName() + ".java");
        assertThat(source.getMd5sum()).isEqualTo(md5sumOf(bytesAt(urlOf(ClassWithInnerClass.Inner.class))));

        JavaClass clazzFromJar = new ClassFileImporter().importClass(Rule.class);
        source = clazzFromJar.getSource().get();
        assertThat(source.getUri()).isEqualTo(urlOf(Rule.class).toURI());
        assertThat(source.getFileName()).contains(Rule.class.getSimpleName() + ".java");
        assertThat(source.getMd5sum()).isEqualTo(md5sumOf(bytesAt(urlOf(Rule.class))));

        ArchConfiguration.get().setMd5InClassSourcesEnabled(false);
        source = new ClassFileImporter().importClass(ClassToImportOne.class).getSource().get();
        assertThat(source.getMd5sum()).isEqualTo(MD5_SUM_DISABLED);
    }

    @Test
    public void imports_class_objects() {
        JavaClasses classes = new ClassFileImporter().importClasses(ClassToImportOne.class, ClassToImportTwo.class);

        assertThatTypes(classes).matchInAnyOrder(ClassToImportOne.class, ClassToImportTwo.class);
    }

    /**
     * Compare {@link LocationsTest#locations_of_packages_within_JAR_URIs_that_do_not_contain_package_folder()}
     */
    @Test
    public void imports_packages_even_if_jar_entry_for_package_is_missing() {
        String packageToImport = independentClasspathRule.getIndependentTopLevelPackage();

        ClassFileImporter classFileImporter = new ClassFileImporter();
        JavaClasses classes = classFileImporter.importPackages(packageToImport);
        assertThat(classes).extracting("name")
                .doesNotContain(independentClasspathRule.getNameOfSomeContainedClass());

        independentClasspathRule.configureClasspath();

        classes = classFileImporter.importUrl(independentClasspathRule.getOnlyUrl());
        assertThat(classes).extracting("name")
                .containsAll(independentClasspathRule.getNamesOfClasses());
        assertThat(classes).extracting("packageName")
                .containsAll(independentClasspathRule.getPackagesOfClasses());

        classes = classFileImporter.importPackages(packageToImport);
        assertThat(classes).extracting("name").contains(independentClasspathRule.getNameOfSomeContainedClass());
    }

    @Test
    public void imports_paths() throws Exception {
        File exampleFolder = new File(new File(urlOf(getClass()).toURI()).getParentFile(), "testexamples");
        File folderOne = new File(exampleFolder, "pathone");
        File folderTwo = new File(exampleFolder, "pathtwo");

        JavaClasses classes = new ClassFileImporter()
                .importPaths(ImmutableList.of(folderOne.toPath(), folderTwo.toPath()));
        assertThatTypes(classes).matchInAnyOrder(Class11.class, Class12.class, Class21.class, Class22.class);

        classes = new ClassFileImporter().importPaths(folderOne.toPath(), folderTwo.toPath());
        assertThatTypes(classes).matchInAnyOrder(Class11.class, Class12.class, Class21.class, Class22.class);

        classes = new ClassFileImporter().importPaths(folderOne.getAbsolutePath(), folderTwo.getAbsolutePath());
        assertThatTypes(classes).matchInAnyOrder(Class11.class, Class12.class, Class21.class, Class22.class);

        classes = new ClassFileImporter().importPath(folderOne.toPath());
        assertThatTypes(classes).matchInAnyOrder(Class11.class, Class12.class);

        classes = new ClassFileImporter().importPath(folderOne.getAbsolutePath());
        assertThatTypes(classes).matchInAnyOrder(Class11.class, Class12.class);
    }

    @Test
    public void ImportOptions_are_respected() throws Exception {
        ClassFileImporter importer = new ClassFileImporter().withImportOption(importOnly(getClass(), Rule.class));

        assertThatTypes(importer.importPath(Paths.get(urlOf(getClass()).toURI()))).matchExactly(getClass());
        assertThatTypes(importer.importUrl(urlOf(getClass()))).matchExactly(getClass());
        assertThatTypes(importer.importJar(jarFileOf(Rule.class))).matchExactly(Rule.class);
    }

    @Test
    public void is_resilient_against_broken_ClassFileSources() throws MalformedURLException {
        JavaClasses classes = new ClassFileImporter().importUrl(new File("/broken.class").toURI().toURL());
        assertThat(classes).isEmpty();

        classes = new ClassFileImporter().importUrl(new File("/broken.jar").toURI().toURL());
        assertThat(classes).isEmpty();
    }

    private void assertSameSimpleNameOfArchUnitAndReflection(ImportedClasses classes, String className) throws ClassNotFoundException {
        assertSameSimpleNameOfArchUnitAndReflection(classes, Class.forName(className));
    }

    private void assertSameSimpleNameOfArchUnitAndReflection(ImportedClasses classes, Class<?> clazz) {
        assertThat(classes.get(clazz.getName()).getSimpleName()).isEqualTo(clazz.getSimpleName());
    }

    private Set<Dependency> withoutJavaLangTargets(Set<Dependency> dependencies) {
        Set<Dependency> result = new HashSet<>();
        for (Dependency dependency : dependencies) {
            if (!dependency.getTargetClass().getPackageName().startsWith("java.lang")) {
                result.add(dependency);
            }
        }
        return result;
    }

    private void copyClassFile(Class<?> clazz, File targetFolder) throws IOException, URISyntaxException {
        Files.copy(Paths.get(urlOf(clazz).toURI()), new File(targetFolder, clazz.getSimpleName() + ".class").toPath());
    }

    static JarFile jarFileOf(Class<?> clazzInJar) throws IOException {
        URLConnection connection = urlOf(clazzInJar).openConnection();
        checkArgument(connection instanceof JarURLConnection, "Class %s is not contained in a JAR", clazzInJar.getName());
        return ((JarURLConnection) connection).getJarFile();
    }

    private ImportOption importOnly(final Class<?>... classes) {
        return new ImportOption() {
            @Override
            public boolean includes(Location location) {
                for (Class<?> c : classes) {
                    if (location.contains(urlOf(c).getFile())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private Condition<MethodCallTarget> targetWithFullName(final String name) {
        return new Condition<MethodCallTarget>(String.format("target with name '%s'", name)) {
            @Override
            public boolean matches(MethodCallTarget value) {
                return value.getFullName().equals(name);
            }
        };
    }

    private Constructor<?> reflect(ConstructorCallTarget target) {
        return reflect(target.resolveConstructor().get());
    }

    private Constructor<?> reflect(JavaConstructor javaConstructor) {
        try {
            return javaConstructor.getOwner().reflect().getConstructor(asClasses(javaConstructor.getRawParameterTypes()));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Method reflect(MethodCallTarget target) {
        return reflect(getOnlyElement(target.resolve()));
    }

    private Method reflect(JavaMethod javaMethod) {
        try {
            return javaMethod.getOwner().reflect().getMethod(javaMethod.getName(), asClasses(javaMethod.getRawParameterTypes()));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private JavaClassList targetParametersOf(Set<JavaMethodCall> calls, String name) {
        return findAnyByName(calls, name).getTarget().getRawParameterTypes();
    }

    private JavaClass returnTypeOf(Set<JavaMethodCall> calls, String name) {
        return findAnyByName(calls, name).getTarget().getRawReturnType();
    }

    private JavaFieldAccess getOnly(Set<JavaFieldAccess> fieldAccesses, String name, AccessType accessType) {
        return getOnlyElement(getByNameAndAccessType(fieldAccesses, name, accessType));
    }

    private Set<JavaFieldAccess> getByNameAndAccessType(Set<JavaFieldAccess> fieldAccesses, String name, AccessType accessType) {
        Set<JavaFieldAccess> result = new HashSet<>();
        for (JavaFieldAccess access : fieldAccesses) {
            if (name.equals(access.getName()) && access.getAccessType() == accessType) {
                result.add(access);
            }
        }
        return result;
    }

    private <T extends HasOwner<JavaCodeUnit>> T getOnlyByCaller(Set<T> calls, JavaCodeUnit caller) {
        return getOnlyElement(getByCaller(calls, caller));
    }

    private <T extends JavaAccess<?>> Set<T> getByTarget(Set<T> calls, final JavaConstructor target) {
        return getBy(calls, new Predicate<JavaAccess<?>>() {
            @Override
            public boolean apply(JavaAccess<?> input) {
                return targetFrom(target).getFullName().equals(input.getTarget().getFullName());
            }
        });
    }

    private <T extends JavaAccess<?>> Set<T> getByTargetOwner(Set<T> calls, Class<?> targetOwner) {
        return getByTargetOwner(calls, targetOwner.getName());
    }

    private <T extends JavaAccess<?>> Set<T> getByTargetOwner(Set<T> calls, final String targetOwnerName) {
        return getBy(calls, targetOwnerNameEquals(targetOwnerName));
    }

    private Predicate<JavaAccess<?>> targetOwnerNameEquals(final String targetFqn) {
        return new Predicate<JavaAccess<?>>() {
            @Override
            public boolean apply(JavaAccess<?> input) {
                return targetFqn.equals(input.getTarget().getOwner().getName());
            }
        };
    }

    private <T extends JavaAccess<?>> Set<T> getByTargetOwner(Set<T> calls, final JavaClass targetOwner) {
        return getBy(calls, new Predicate<T>() {
            @Override
            public boolean apply(T input) {
                return targetOwner.equals(input.getTarget().getOwner());
            }
        });
    }

    private <T extends HasOwner<JavaCodeUnit>> Set<T> getByCaller(Set<T> calls, final JavaCodeUnit caller) {
        return getBy(calls, new Predicate<T>() {
            @Override
            public boolean apply(T input) {
                return caller.equals(input.getOwner());
            }
        });
    }

    private <T extends HasOwner<JavaCodeUnit>> Set<T> getBy(Set<T> calls, Predicate<? super T> predicate) {
        return FluentIterable.from(calls).filter(predicate).toSet();
    }

    private Set<FieldAccessTarget> targetsOf(Set<JavaFieldAccess> fieldAccesses) {
        Set<FieldAccessTarget> result = new HashSet<>();
        for (JavaFieldAccess access : fieldAccesses) {
            result.add(access.getTarget());
        }
        return result;
    }

    private Set<Integer> lineNumbersOf(Set<JavaFieldAccess> fieldAccesses) {
        Set<Integer> result = new HashSet<>();
        for (JavaFieldAccess access : fieldAccesses) {
            result.add(access.getLineNumber());
        }
        return result;
    }

    private <T extends HasName> Set<T> getByName(Iterable<T> thingsWithName, String name) {
        Set<T> result = new HashSet<>();
        for (T hasName : thingsWithName) {
            if (name.equals(hasName.getName())) {
                result.add(hasName);
            }
        }
        return result;
    }

    private <T extends HasName> T findAnyByName(Iterable<T> thingsWithName, String name) {
        T result = getFirst(getByName(thingsWithName, name), null);
        return checkNotNull(result, "No object with name '" + name + "' is present in " + thingsWithName);
    }

    private ImportedClasses classesIn(String path) throws Exception {
        return new ImportedClasses(path);
    }

    private class ImportedClasses extends ForwardingCollection<JavaClass> {
        private final ClassFileImporter importer = new ClassFileImporter();
        private final JavaClasses classes;

        private ImportedClasses(String path) throws Exception {
            classes = importer.importPath(Paths.get(ClassFileImporterTest.this.getClass().getResource(path).toURI()));
        }

        JavaClass get(Class<?> clazz) {
            return get(clazz.getName());
        }

        private JavaClass get(String className) {
            return findAnyByName(classes, className);
        }

        @Override
        protected Collection<JavaClass> delegate() {
            return classes;
        }

        Set<JavaCodeUnit> getCodeUnits() {
            Set<JavaCodeUnit> codeUnits = new HashSet<>();
            for (JavaClass clazz : classes) {
                codeUnits.addAll(clazz.getCodeUnits());
            }
            return codeUnits;
        }

        Set<JavaMethod> getMethods() {
            Set<JavaMethod> methods = new HashSet<>();
            for (JavaClass clazz : classes) {
                methods.addAll(clazz.getMethods());
            }
            return methods;
        }

        Set<JavaField> getFields() {
            Set<JavaField> fields = new HashSet<>();
            for (JavaClass clazz : classes) {
                fields.addAll(clazz.getFields());
            }
            return fields;
        }
    }
}
