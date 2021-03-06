package lanchon.dexpatcher;

import java.util.Collection;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.immutable.ImmutableClassDef;

import static lanchon.dexpatcher.Logger.Level.*;
import static org.jf.dexlib2.AccessFlags.*;

// TODO: Warn about changes in superclass and interfaces.

public class ClassSetPatcher extends SimplePatcher<ClassDef> {

	public ClassSetPatcher(Logger logger) {
		super(logger, null);
	}

	// Adapters

	@Override
	protected String getId(ClassDef t) {
		return t.getType();
	}

	@Override
	protected String getLogPrefix(String id, ClassDef t) {
		return "type '" + Util.getTypeNameFromDescriptor(id) + "'";
	}

	@Override
	protected String getTargetLogPrefix(String targetId, PatcherAnnotation annotation) {
		return "target '" + Util.getTypeNameFromDescriptor(targetId) + "'";
	}

	// Handlers

	@Override
	protected Action getDefaultAction(String patchId, ClassDef patch) {
		return Action.ADD;
	}

	@Override
	protected void onPrepare(String patchId, ClassDef patch, PatcherAnnotation annotation) throws PatchException {
		if (annotation.getRecursive()) PatcherAnnotation.throwInvalidElement(Tag.ELEM_RECURSIVE);
	}

	@Override
	protected String getTargetId(String patchId, ClassDef patch, PatcherAnnotation annotation) {
		String target = annotation.getTarget();
		String targetClass = annotation.getTargetClass();
		String targetId;
		if (target != null) {
			if (Util.isTypeDescriptor(target)) {
				targetId = target;
			} else {
				String base = Util.getTypeNameFromDescriptor(patchId);
				targetId = Util.getTypeDescriptorFromName(Util.resolveTypeName(target, base));
			}
		} else if (targetClass != null) {
			targetId = targetClass;
		} else {
			targetId = patchId;
		}
		return targetId;
	}

	@Override
	protected ClassDef onSimpleAdd(ClassDef patch, PatcherAnnotation annotation) {
		if (patch.getAnnotations() == annotation.getFilteredAnnotations()) {
			return patch;	// avoid creating a new object unless necessary
		}
		return new ImmutableClassDef(
				patch.getType(),
				patch.getAccessFlags(),
				patch.getSuperclass(),
				patch.getInterfaces(),
				patch.getSourceFile(),
				annotation.getFilteredAnnotations(),
				patch.getStaticFields(),
				patch.getInstanceFields(),
				patch.getDirectMethods(),
				patch.getVirtualMethods());
	}

	@Override
	protected ClassDef onSimpleEdit(ClassDef patch, PatcherAnnotation annotation, ClassDef target, boolean renaming) {

		if (!annotation.getOnlyEditMembers()) {
			int flags1 = Util.getClassAccessFlags(patch);
			int flags2 = Util.getClassAccessFlags(target);
			// Avoid duplicated messages if not renaming.
			if (renaming) {
				String message = "'%s' modifier mismatch in targeted and edited types";
				if (isLogging(WARN)) checkAccessFlags(WARN, flags1, flags2,
						new AccessFlags[] { STATIC, FINAL, INTERFACE, ABSTRACT, ANNOTATION, ENUM }, message);
				if (isLogging(DEBUG)) checkAccessFlags(DEBUG, flags1, flags2,
						new AccessFlags[] { PUBLIC, PRIVATE, PROTECTED, SYNTHETIC }, message);
			} else {
				String message = "'%s' modifier mismatch in original and edited versions";
				if (isLogging(WARN)) checkAccessFlags(WARN, flags1, flags2,
						new AccessFlags[] { STATIC, FINAL, INTERFACE, ABSTRACT, ANNOTATION, ENUM }, message);
				if (isLogging(INFO)) checkAccessFlags(INFO, flags1, flags2,
						new AccessFlags[] { PUBLIC, PRIVATE, PROTECTED }, message);
				if (isLogging(DEBUG)) checkAccessFlags(DEBUG, flags1, flags2,
						new AccessFlags[] { SYNTHETIC }, message);
			}
		}

		ClassDef source;
		Collection<? extends Annotation> annotations;
		if (annotation.getOnlyEditMembers()) {
			source = target;
			annotations = target.getAnnotations();
		} else {
			source = patch;
			annotations = annotation.getFilteredAnnotations();
		}

		return new ImmutableClassDef(
				patch.getType(),
				source.getAccessFlags(),
				source.getSuperclass(),
				source.getInterfaces(),
				source.getSourceFile(),
				annotations,
				new FieldSetPatcher(this, "static field", annotation)
						.process(target.getStaticFields(), patch.getStaticFields()),
				new FieldSetPatcher(this, "instance field", annotation)
						.process(target.getInstanceFields(), patch.getInstanceFields()),
				new DirectMethodSetPatcher(this, "direct method", annotation)
						.process(target.getDirectMethods(), patch.getDirectMethods()),
				new MethodSetPatcher(this, "virtual method", annotation)
						.process(target.getVirtualMethods(), patch.getVirtualMethods()));

	}

	@Override
	protected void onEffectiveReplacement(String id, ClassDef patched, ClassDef original, boolean editedInPlace) {
		// Avoid duplicated messages if not renaming.
		if (!editedInPlace) {
			int flags1 = Util.getClassAccessFlags(patched);
			int flags2 = Util.getClassAccessFlags(original);
			String message = "'%s' modifier mismatch in original and replacement types";
			if (isLogging(WARN)) checkAccessFlags(WARN, flags1, flags2,
					new AccessFlags[] { STATIC, FINAL, INTERFACE, ABSTRACT, ANNOTATION, ENUM }, message);
			if (isLogging(INFO)) checkAccessFlags(INFO, flags1, flags2,
					new AccessFlags[] { PUBLIC, PRIVATE, PROTECTED }, message);
			if (isLogging(DEBUG)) checkAccessFlags(DEBUG, flags1, flags2,
					new AccessFlags[] { SYNTHETIC }, message);
		}
	}

}
