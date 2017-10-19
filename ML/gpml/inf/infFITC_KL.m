function [post nlZ dnlZ] = infFITC_KL(hyp, mean, cov, lik, x, y)
% Batch implementation based on Scalable Variational Gaussian Process Classification

persistent last_alpha                                   % copy of the last alpha
if any(isnan(last_alpha)), last_alpha = zeros(size(last_alpha)); end   % prevent

inf = 'infLaplace';

cov1 = cov{1}; if isa(cov1, 'function_handle'), cov1 = func2str(cov1); end
if ~strcmp(cov1,'covFITC'); error('Only covFITC supported.'), end    % check cov
if isfield(hyp,'xu'), cov{3} = hyp.xu; end  % hyp.xu is provided, replace cov{3}

[diagK,Kuu,Ku] = feval(cov{:}, hyp.cov, x);         % evaluate covariance matrix
if ~isempty(hyp.lik)                          % hard coded inducing inputs noise
  sn2 = exp(2*hyp.lik(end)); snu2 = 1e-6*sn2;               % similar to infFITC
else
  snu2 = 1e-6;
end
[n, D] = size(x); nu = size(Kuu,1);
m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector
Kuu=Kuu+snu2*eye(nu);
R = chol(Kuu);           % initial R, used for refresh O(nu^3)
%R'*R = K_m
                      

lik_name = func2str(lik{1});
rot180   = @(A)   rot90(rot90(A));                     % little helper functions
chol_inv = @(A) rot180(chol(rot180(A))')\eye(nu);                 % chol(inv(A))
R0 = chol_inv(Kuu);           % initial R, used for refresh O(nu^3)
V = R0*Ku; d0 = diagK-sum(V.*V,1)';    % initial d, needed for refresh O(n*nu^2)
const_term=-0.5*nu+sum(log(diag(R)));

nlZ=Inf;
H=R0'*V;
%alpha = zeros(nu,1);
%C=eye(nu);
%m_rate=0.5;
%m_alpha=zeros(nu,1);
%m_C=zeros(nu,nu);
m_0 = feval(mean{:}, hyp.mean, cov{3}); 
m_u = m_0;
%K_m=Kuu;

%V_u = Kuu;
C_u = chol(Kuu,'lower');
C_u = C_u-diag(diag(C_u))+diag(log(diag(C_u)));

using_lbfgs=hyp.using_lbfgs;
if using_lbfgs==1
	alla=[m_u; reshape(C_u,nu*nu,1)];
	[alla nlZ] = lbfgs(alla, y,lik,nu, m, Ku, R0, V, hyp,R, const_term, d0, m_0, H, lik_name);
	m_u=alla(1:nu,1);
	C_u=reshape(alla(nu+1:end,1),nu,nu);
else
	it = 0;   
	maxit = hyp.max_pass; 
	rate=hyp.learning_rate;
	while it<maxit
		%alpha=R0'*R0*(m_u-m_0);
		alpha=Kuu\(m_u-m_0);
		post_m=m+Ku'*alpha;

		%C=chol(V_u,'lower');
		C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));
		%tmp1=C'*R0';
		%V1=tmp1*V;)

		V1=C'*(Kuu\Ku);
		post_v=d0+sum(V1.*V1,1)';

		if hyp.stochastic_approx==1
			[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
		else
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
			otherwise	 
				[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
			end
		end

		nlZ_it=batch_nlz_sparse(mean, lik, x,  y,  hyp, R0, Ku, m_0, diagK, R, C, m_u);
		fprintf('%d) %.4f\n', it, nlZ_it);

		g_rate=rate/(2.0+it)^(hyp.power);
		%g_rate=rate;

		g_m_u=alpha-H*df;
		m_u=m_u-g_rate*g_m_u;


		%g_C_u=diag(-1.0./diag(C))+tril(R0'*R0*C - H*diag(2.0*dv)*H'*C);
		%g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) );
		%g_C_u=tril(R0'*R0*C - H*diag(2.0*dv)*H'*C);
		g_C_u=tril(Kuu\C - H*diag(2.0*dv)*H'*C);
		g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) )+ diag(-1 .* ones(nu,1));
		C_u=C_u-g_rate*g_C_u;

		%inv_C=C\eye(nu);
		%g_V_u=0.5*(R0'*R0 - inv_C'*inv_C)-H*diag(dv)*H';
		%V_u=V_u-g_rate*g_V_u;

		it=it+1;
	end
	%C=chol(V_u,'lower');
end
C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));
%alpha=R0'*R0*(m_u-m_0);
alpha=Kuu\(m_u-m_0);

tmp1=C'*R0';
post.alpha=alpha;
post.L=R0'*(tmp1'*tmp1-eye(nu))*R0;
post.sW=zeros(n);


nlZ=batch_nlz_sparse(mean, lik, x,  y,  hyp,  R0, Ku, m_0, diagK, R, C, m_u);
fprintf('final: %.4f\n', nlZ);


if nargout>2                                           % do we want derivatives?
  warning('to be implemented\n');
  dnlZ = NaN;
end


function [nlZ,dnlZ] = margLik(alla,y,lik,nu, m, Ku, R0, V, hyp,R, const_term, d0, m_0, H, lik_name)
	m_u=alla(1:nu,1);
	C_u=reshape(alla(nu+1:end,1),nu,nu);


	post_m=m+H'*(m_u-m_0);
	%C=chol(V_u,'lower');
	C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));

	tmp1=C'*R0';
	V1=tmp1*V;
	post_v=d0+sum(V1.*V1,1)';
	flag=0;

	%if any(post_v<1e-10)
		%flag=1;
	%end


	if hyp.stochastic_approx==1
		[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
	else
		switch lik_name
		case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
			[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
		otherwise	 
			[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
		end
	end

	alpha=R0'*R0*(m_u-m_0);
	tmp2=R*alpha;
	nlZ=-sum(ll)+0.5*(sum(sum((tmp1.*tmp1)))+tmp2'*tmp2)-sum(log(diag(C)))+const_term;

	g_m_u=alpha-H*df;

	%g_C_u=diag(-1.0./diag(C))+tril(R0'*R0*C - H*diag(2.0*dv)*H'*C);
	%g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) );
	g_C_u=tril(R0'*R0*C - H*diag(2.0*dv)*H'*C);
	g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) )+ diag(-1 .* ones(nu,1));

	dnlZ=[g_m_u; reshape(g_C_u,nu*nu,1)];

	if ~isLegal(dnlZ)
		nlZ=inf;
		dnlZ(isnan(dnlZ))=0;
		dnlZ(isinf(dnlZ))=0;
	end

	%if flag
		%nlZ=inf;
	%end

function [alla nlZ] = lbfgs(alla, y,lik,nu, m, Ku, R0, V, hyp,R, const_term, d0, m_0, H, lik_name)
	optMinFunc = struct('Display', 'FULL',...
    'Method', 'lbfgs',...
    'DerivativeCheck', 'off',...
    'LS_type', 1,...
    'MaxIter', 1000,...
	'LS_interp', 1,...
    'MaxFunEvals', 1000000,...
    'Corr' , 100,...
    'optTol', 1e-15,...
    'progTol', 1e-15);
	[alla, nlZ] = minFunc(@margLik, alla, optMinFunc, y,lik,nu, m, Ku, R0, V, hyp,R, const_term, d0, m_0, H, lik_name);
