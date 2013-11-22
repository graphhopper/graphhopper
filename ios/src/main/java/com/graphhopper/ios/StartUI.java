/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.ios;

import org.robovm.cocoatouch.coregraphics.CGRect;
import org.robovm.cocoatouch.foundation.NSAutoreleasePool;
import org.robovm.cocoatouch.foundation.NSDictionary;
import org.robovm.cocoatouch.uikit.*;


/**
 * @author Peter Karich
 */
public class StartUI extends UIApplicationDelegate.Adapter
{

    private UIWindow window = null;
    private int clickCount = 0;

    @Override
    public boolean didFinishLaunching( UIApplication application,
            NSDictionary launchOptions )
    {

        final UIButton button = UIButton.fromType(UIButtonType.RoundedRect);
        button.setFrame(new CGRect(115.0f, 121.0f, 91.0f, 37.0f));
        button.setTitle("Click me!", UIControlState.Normal);

        button.addOnTouchUpInsideListener(new UIControl.OnTouchUpInsideListener()
        {
            @Override
            public void onTouchUpInside( UIControl control, UIEvent event )
            {
                button.setTitle("Click #" + (++clickCount), UIControlState.Normal);
            }
        });

        window = new UIWindow(UIScreen.getMainScreen().getBounds());
        window.setBackgroundColor(UIColor.lightGrayColor());
        window.addSubview(button);
        window.makeKeyAndVisible();

        return true;
    }

    public static void main( String[] args )
    {
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(args, null, StartUI.class);
        pool.drain();
    }
}
