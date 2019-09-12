<p class="lead">Insanely fast and accurate (<b>99.8%</b>) MICR E-13B & CMC-7 detectors and recognizers using deep learning. </p>
            <p> 
                Automating Bank account information extraction from MICR (Magnetic ink character recognition) zones on scanned checks/document <b>above human-level accuracy</b> is a very challenging task. 
                Our implementation reaches such level of accuracy using latest deep learning techniques. We outperforms both <a href="https://www.abbyy.com/ocr_sdk/" target="_blank">ABBYY</a> and <a href="https://demo.leadtools.com/JavaScript/BankCheckReader/" target="_blank">LEADTOLS</a> in terms of accuracy and speed (<b>almost #30 times faster</b>). <br />
                Using a single model we're able to accurately locate the MICR zones, infer the type (<b>E-13B</b> or <b><span style="font-family: FontCMC7;">CMC-7</span></b>) and recognize the fields: one-shot deep model.
                The performance gap between us and the other companies is more important for <span style="font-family: FontCMC7;">CMC-7</span> format which is more challenging than E13B. <br /> <br />
                This technology is a key component of <a href="https://www.remotedepositcapture.com/overview/rdc.overview.aspx" target="_blank">Remote Deposit Capture</a> applications using mobile phones or scanners. <br />
            </p>

## Online demo ##
Don't take our word for it, test it using your own images at https://www.doubango.org/webapps/micr/

## Source code ##
Source code coming soon... and:
 * The Computer vision part is open source: https://github.com/DoubangoTelecom/CompV <br />
 * The Deep learning part is closed-source for now: https://github.com/DoubangoTelecom/ultimateMICR <br />
