/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hasor.test.web.j2ee.error;
import java.io.Writer;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.hasor.context.AppContext;
import org.hasor.servlet.ErrorHook;
import org.hasor.servlet.anno.WebError;
/**
 * ���ദ��servlet�ڵ��ù������׳��Ŀ�ָ���쳣
 * @version : 2013-8-11
 * @author ������ (zyc@byshell.org)
 */
@WebError(NullPointerException.class)
public class NullPointerExceptionHook implements ErrorHook {
    @Override
    public void init(AppContext appContext) {
        /*�����յ�Web init�ź�ʱ�����*/
    }
    @Override
    public void doError(ServletRequest request, ServletResponse response, Throwable error) throws Throwable {
        Writer w = response.getWriter();
        w.write("is NullPointerException.");
        w.flush();
    }
    @Override
    public void destroy(AppContext appContext) {
        /*�����յ�Web�����ź�ʱ�����*/
    }
}