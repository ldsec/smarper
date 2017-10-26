# SmarPer
SmarPer: Context-Aware and Automatic Runtime-Permissions for Mobile Devices

Smart Permissions ([SmarPer](https://spism.epfl.ch/smarper/)) is an advanced-permission mechanism for Android, with support for finer-grained permissions, context-awareness and multiple decision-levels. In addition, to help users manage permissions more efficiently and reduce permission fatigue, SmarPer provides (semi-) automatic decisions. In doing so, we will provide users with smarter controls for protecting their private information, with a lower overhead for users. We provide the source code for the SmarPer prototype in Android and the Machine Learning framework used in the SmarPer project.

# Structure of the code
This project is organized as follows:
- `SmarPerApp/`: SmarPer prototype in Android, based on [XPrivacy](https://github.com/M66B/XPrivacy).
- `ML/`: Machine Learning framework for carefully training and comparing different context-aware models that predict permission decisions. 

# Documentation
For more details about the SmarPer project, refer to the [research paper](https://hal.archives-ouvertes.fr/hal-01489684) and the [SmarPer website](https://spism.epfl.ch/smarper/).

# Research
The research behind the SmarPer Project was published in the following paper:
- [1] Katarzyna Olejnik, Italo Dacosta, Joana Soares Machado, Kévin Huguenin, Mohammad Emtiyaz Khan, Jean-Pierre Hubaux. SmarPer: Context-Aware and Automatic Runtime-Permissions for Mobile Devices. In Proceedings of the 38th IEEE Symposium on Security and Privacy (S&P), San Jose, CA, United States, May 2017.

# License
SmarPer and [XPrivacy](https://github.com/M66B/XPrivacy) are released under the GPLv3 License.

The GPML toolbox code is released under the FreeBSD License.

# Contact
The SmarPer project is part of the [research effort](http://lca.epfl.ch/projects/privacy-mobile-pervasive/) of the LCA1 lab, EPFL. Feel free to contact us (smarper@epfl.ch).

Project members:
- Kasia Olejnik
- Italo Dacosta
- Joana Soares Machado
- Kevin Huguenin
- Mohammad Emtiyaz Khan
- Jean-Pierre Hubaux
