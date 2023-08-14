/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.request;

import java.util.ArrayList;
import java.util.List;
import net.opengis.wfs.DeleteElementType;
import net.opengis.wfs.WfsFactory;
import net.opengis.wfs.impl.DeleteElementTypeImpl;
import net.opengis.wfs20.impl.DeleteTypeImpl;
import org.eclipse.emf.ecore.EObject;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.xsd.EMFUtils;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;

/**
 * Delete element in a Transaction request.
 *
 * @author Justin Deoliveira, OpenGeo
 */
public abstract class Delete extends TransactionElement {

    protected Delete(EObject adaptee) {
        super(adaptee);
    }

    @Override
    public abstract Filter getFilter();

    public abstract void addFilter(Filter filter);

    public static class WFS11 extends Delete {
        public WFS11(EObject adaptee) {
            super(adaptee);
        }

        @Override
        public Filter getFilter() {
            Filter filter = eGet(adaptee, "filter", Filter.class);
            return filter;
        }

        @Override
        public void addFilter(Filter filter) {
            eAddForDelete(adaptee, "filter", filter);
        }

        public static DeleteElementType unadapt(Delete delete) {
            DeleteElementType de = WfsFactory.eINSTANCE.createDeleteElementType();
            de.setHandle(delete.getHandle());
            de.setTypeName(delete.getTypeName());
            de.setFilter(delete.getFilter());
            return de;
        }
    }

    public static class WFS20 extends Delete {
        public WFS20(EObject adaptee) {
            super(adaptee);
        }

        @Override
        public Filter getFilter() {
            Filter filter = eGet(adaptee, "filter", Filter.class);
            return filter;
        }

        @Override
        public void addFilter(Filter filter) {
            eAddForDelete(adaptee, "filter", filter);
        }
    }

    protected void eAddForDelete(EObject obj, String property, Filter newFilter) {
        FilterFactory ff = CommonFactoryFinder.getFilterFactory2();
        Filter currentFilter = (Filter) EMFUtils.get(obj, property);

        List<Filter> filters = new ArrayList<>();
        if (newFilter != null) {
            filters.add(newFilter);
        }
        if (currentFilter != null) {
            filters.add(currentFilter);
        }

        Filter result;
        if (filters.isEmpty()) {
            result = null;
        } else if (filters.size() == 1) {
            result = filters.get(0);
        } else {
            result = ff.or(filters);
        }

        if (obj instanceof DeleteElementTypeImpl) {
            ((DeleteElementTypeImpl) obj).setFilter(result);
        } else if (obj instanceof DeleteTypeImpl) {
            ((DeleteTypeImpl) obj).setFilter(result);
        }
    }
}
