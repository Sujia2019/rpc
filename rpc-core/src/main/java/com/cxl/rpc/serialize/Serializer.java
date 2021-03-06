package com.cxl.rpc.serialize;

import com.cxl.rpc.serialize.impl.GsonSerializer;
import com.cxl.rpc.serialize.impl.JacksonSerializer;
import com.cxl.rpc.serialize.impl.ProtostuffSerializer;

public abstract class Serializer {
    public abstract <T> byte[] serializer(T obj);

    public abstract <T> Object deserializer(byte[] bytes, Class<T> clazz);

    public enum SerializerEnum {

        JACKSON(JacksonSerializer.class),

        PROTOSTUFF(ProtostuffSerializer.class),
        GSON(GsonSerializer.class);

        private Class<? extends Serializer> serializerClass;

        SerializerEnum(Class<? extends Serializer> serializerClass) {
            this.serializerClass = serializerClass;
        }

        public Serializer getSerializer() {
            try {
                return serializerClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static SerializerEnum match(String name, SerializerEnum defaultSerializer) {
            for (SerializerEnum item : SerializerEnum.values()) {
                if (item.name().equals(name)) {
                    return item;
                }
            }
            return defaultSerializer;
        }
    }
}
