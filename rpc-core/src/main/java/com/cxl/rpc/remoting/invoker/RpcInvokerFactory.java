package com.cxl.rpc.remoting.invoker;

import com.cxl.rpc.registry.ServiceRegistry;
import com.cxl.rpc.registry.impl.LocalRegistry;
import com.cxl.rpc.remoting.net.params.BaseCallback;
import com.cxl.rpc.remoting.net.params.RpcFutureResponse;
import com.cxl.rpc.remoting.net.params.RpcResponse;
import com.cxl.rpc.util.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RpcInvokerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcInvokerFactory.class);

    // ---------------------- default instance ----------------------

    private static volatile RpcInvokerFactory instance = new RpcInvokerFactory(LocalRegistry.class, null);

    public static RpcInvokerFactory getInstance() {
        return instance;
    }


    //=========================config===================
    private Class<? extends ServiceRegistry> serviceRegistryClass;
    //class.forname
    private Map<String, String> serviceRegistryParam;


    public RpcInvokerFactory() {
    }

    public RpcInvokerFactory(Class<? extends ServiceRegistry> serviceRegistryClass, Map<String, String> serviceRegistryParam) {
        this.serviceRegistryClass = serviceRegistryClass;
        this.serviceRegistryParam = serviceRegistryParam;
    }
    //---------------------start / stop--------------------

    public void start()throws Exception{
        //start registry
        if (serviceRegistryClass != null) {
            serviceRegistry=serviceRegistryClass.newInstance();
            serviceRegistry.start(serviceRegistryParam);
        }
    }

    public void stop()throws Exception{
        //stop registry
        if (serviceRegistry != null) {
            serviceRegistry.stop();
        }

        //stop callback

        if (stopCallbackList.size()>0) {
            for (BaseCallback callback : stopCallbackList) {
                try {
                    callback.run();
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(),e);
                }
            }
        }

        //stop CallbackThreadPool
        stopCallbackThreadPool();
    }

    //---------------------service registry-------------------
    private ServiceRegistry serviceRegistry;
    public ServiceRegistry getServiceRegistry(){return serviceRegistry;}

    private List<BaseCallback> stopCallbackList=new ArrayList<>();

    public void addStopCallback(BaseCallback callback){
        stopCallbackList.add(callback);
    }

    //------------------------future-response pool----------------------------
    private ConcurrentMap<String, RpcFutureResponse> futureResponsePool=new ConcurrentHashMap<>();

    public void setInvokerFuture(String requestId,RpcFutureResponse futureResponse){
        futureResponsePool.put(requestId
        ,futureResponse);
    }
    public void removeInvokerFuture(String requestId){
        futureResponsePool.remove(requestId);
    }

    public void notifyInvokerFuture(String requestId, final RpcResponse rpcResponse){
        //get
        final RpcFutureResponse futureResponse=futureResponsePool.get(requestId);
        if (futureResponse==null){
            return;
        }

        //notify
        if (futureResponse.getInvokeCallback()!=null) {
            //回调类型
            try {
                executeResponseCallback(() -> {
                   if (null!=rpcResponse.getErrorMsg()){
                       futureResponse.getInvokeCallback().onFailure(new RpcException(rpcResponse.getErrorMsg()));
                   }else{
                       futureResponse.getInvokeCallback().onSuccess(rpcResponse.getResult());
                   }
                });
            } catch (Exception e) {
                LOGGER.error(e.getMessage(),e);
            }
        }else{
            // 其他回调类型
            futureResponse.setResponse(rpcResponse);
        }
        //删除该实例
        futureResponsePool.remove(requestId);
    }
    //------------------response callback ThreadPool-------------------
    private ThreadPoolExecutor threadPoolExecutor=null;
    private void executeResponseCallback(Runnable runnable) {
        if (null == threadPoolExecutor) {
            synchronized (this) {
                if (null == threadPoolExecutor) {
                    threadPoolExecutor = new ThreadPoolExecutor(20, 100, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(1000), r -> new Thread(r, "rpc, RpcInvokerFactory-responseCallbackThreadPool-" + r.hashCode()), (r, executor) -> {
                        throw new RpcException("rpc Invoke Callback Thread pool is EXHAUSTED!");
                    });

                }
            }
        }
        threadPoolExecutor.execute(runnable);
    }



    private void stopCallbackThreadPool() {
        if (null!=threadPoolExecutor){
            threadPoolExecutor.shutdown();
        }
    }
}
