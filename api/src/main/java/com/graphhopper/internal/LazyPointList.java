package com.graphhopper.internal;


import com.graphhopper.bean.RoutePoint;

import java.util.*;

public class LazyPointList implements List<RoutePoint> {

    private List<RoutePoint> instance;

    private final List<double[]> coordinates;

    public LazyPointList(List<double[]> coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public int size() {
        return coordinates.size();// Still Lazy
    }

    @Override
    public boolean isEmpty() {
        return coordinates.isEmpty();// Still Lazy
    }

    @Override
    public boolean contains(Object o) {
        return getInstance().contains(o);
    }

    @Override
    public Iterator<RoutePoint> iterator() {
        return getInstance().iterator();
    }

    @Override
    public Object[] toArray() {
        return getInstance().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return getInstance().toArray(a);
    }

    @Override
    public boolean add(RoutePoint point) {
        return getInstance().add(point);
    }

    @Override
    public boolean remove(Object o) {
        return getInstance().remove(o);// Or immutable?
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return getInstance().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends RoutePoint> c) {
        return getInstance().addAll(c);// Or immutable?
    }

    @Override
    public boolean addAll(int index, Collection<? extends RoutePoint> c) {
        return getInstance().addAll(index, c);// Or immutable?
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return getInstance().removeAll(c);// Or immutable?
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return getInstance().retainAll(c);// Or immutable?
    }

    @Override
    public void clear() {
        getInstance().clear();// Or immutable?
    }

    @Override
    public RoutePoint get(int index) {
        return getInstance().get(index);
    }

    @Override
    public RoutePoint set(int index, RoutePoint element) {
        return getInstance().set(index, element);// Or immutable?
    }

    @Override
    public void add(int index, RoutePoint element) {
        getInstance().add(index, element);// Or immutable?
    }

    @Override
    public RoutePoint remove(int index) {
        return getInstance().remove(index);// Or immutable?
    }

    @Override
    public int indexOf(Object o) {
        return getInstance().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return getInstance().lastIndexOf(o);
    }

    @Override
    public ListIterator<RoutePoint> listIterator() {
        return getInstance().listIterator();
    }

    @Override
    public ListIterator<RoutePoint> listIterator(int index) {
        return getInstance().listIterator(index);
    }

    @Override
    public List<RoutePoint> subList(int fromIndex, int toIndex) {
        return getInstance().subList(fromIndex, toIndex);
    }

    private List<RoutePoint> getInstance() {
        if(instance == null) {
            instance = new ArrayList<RoutePoint>(coordinates.size());

            for(double[] coordinate : coordinates) {
                RoutePoint point = new RoutePoint();
                point.setLatitude(coordinate[0]);
                point.setLongitude(coordinate[1]);

                if(coordinate.length == 3) {
                    point.setElevation(coordinate[2]);
                }

                instance.add(point);
            }
        }

        return instance;
    }
}
