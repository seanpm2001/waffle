package org.waffle.taglib.form;

import java.io.IOException;

/**
 * Form styles.
 * 
 * @author Guilherme Silveira
 * @since 1.0
 */
public interface FormStyle {

	void addLine(String label) throws IOException;

	void beginForm() throws IOException;

	void finishForm() throws IOException;

	void finishLine() throws IOException;

	void addErrors(String label) throws IOException;

}
